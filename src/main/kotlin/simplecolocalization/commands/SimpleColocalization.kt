package simplecolocalization.commands

import ij.IJ
import ij.ImagePlus
import ij.WindowManager
import ij.gui.MessageDialog
import ij.plugin.ChannelSplitter
import ij.plugin.ZProjector
import ij.plugin.frame.RoiManager
import java.io.File
import net.imagej.ImageJ
import org.scijava.ItemVisibility
import org.scijava.command.Command
import org.scijava.log.LogService
import org.scijava.plugin.Parameter
import org.scijava.plugin.Plugin
import org.scijava.table.DefaultGenericTable
import org.scijava.table.IntColumn
import org.scijava.ui.UIService
import org.scijava.widget.NumberWidget
import simplecolocalization.services.CellColocalizationService
import simplecolocalization.services.CellSegmentationService
import simplecolocalization.services.cellcomparator.PixelCellComparator
import simplecolocalization.services.colocalizer.BucketedNaiveColocalizer
import simplecolocalization.services.colocalizer.PositionedCell
import simplecolocalization.services.colocalizer.TransductionAnalysis
import simplecolocalization.services.colocalizer.output.CSVColocalizationOutput
import simplecolocalization.services.colocalizer.output.ImageJTableColocalizationOutput

@Plugin(type = Command::class, menuPath = "Plugins > Simple Cells > Simple Colocalization")
class SimpleColocalization : Command {

    @Parameter
    private lateinit var logService: LogService

    @Parameter
    private lateinit var cellSegmentationService: CellSegmentationService

    @Parameter
    private lateinit var cellColocalizationService: CellColocalizationService

    /**
     * Entry point for UI operations, automatically handling both graphical and
     * headless use of this plugin.
     */
    @Parameter
    private lateinit var uiService: UIService

    @Parameter(
        label = "Output Parameters:",
        visibility = ItemVisibility.MESSAGE,
        required = false
    )
    private lateinit var outputParametersHeader: String

    /**
     * The user can optionally output the results to a file.
     */
    object OutputDestination {
        const val DISPLAY = "Display in table"
        const val CSV = "Save as CSV file"
        const val XML = "Save as XML file"
    }

    @Parameter(
        label = "Results Output:",
        choices = [OutputDestination.DISPLAY, OutputDestination.CSV],
        required = true,
        persist = false,
        style = "radioButtonVertical"
    )
    private var outputDestination = OutputDestination.DISPLAY

    @Parameter(
        label = "Output File (if saving):",
        style = "save",
        required = false
    )
    private var outputFile: File? = null

    /**
     * Specify the channel for the target cell. ImageJ does not have a way to retrieve
     * the channels available at the parameter initiation stage.
     * By default this is 1 (red) channel.
     */
    @Parameter(
        label = "Target Cell Channel:",
        min = "1",
        stepSize = "1",
        required = true,
        persist = false
    )
    private var targetChannel = 1

    /**
     * Specify the channel for the transduced cells.
     * By default this is the 2 (green) channel.
     */
    @Parameter(
        label = "Transduced Cell Channel:",
        min = "1",
        stepSize = "1",
        required = true,
        persist = false
    )
    private var transducedChannel = 2

    @Parameter(
        label = "Preprocessing Parameters:",
        visibility = ItemVisibility.MESSAGE,
        required = false
    )
    private lateinit var preprocessingParamsHeader: String

    /**
     * Applied to the input image to reduce sensitivity of the thresholding
     * algorithm. Higher value means more blur.
     */
    @Parameter(
        label = "Gaussian Blur Sigma (Radius)",
        description = "Reduces sensitivity to cell edges by blurring the " +
            "overall image. Higher is less sensitive.",
        min = "0.0",
        stepSize = "1.0",
        style = NumberWidget.SPINNER_STYLE,
        required = true,
        persist = false
    )
    private var gaussianBlurSigma = 3.0

    @Parameter(
        label = "Cell Identification Parameters:",
        visibility = ItemVisibility.MESSAGE,
        required = false
    )
    private lateinit var identificationParamsHeader: String

    /**
     * Used during the cell identification stage to reduce overlapping cells
     * being grouped into a single cell.
     *
     * TODO(#5): Figure out what this value should be.
     */
    @Parameter(
        label = "Largest Cell Diameter",
        min = "5.0",
        stepSize = "1.0",
        style = NumberWidget.SPINNER_STYLE,
        required = true,
        persist = false
    )
    private var largestCellDiameter = 30.0

    override fun run() {
        var image = WindowManager.getCurrentImage()
        if (image != null) {
            if (image.nSlices > 1) {
                // Flatten slices of the image. This step should probably be done during the preprocessing step - however
                // this operation is not done in-place but creates a new image, which makes this hard.
                image = ZProjector.run(image, "max")
            }

            process(image)
        } else {
            MessageDialog(IJ.getInstance(), "Error", "There is no file open")
        }
    }

    /** Processes single image. */
    private fun process(image: ImagePlus) {
        // TODO(sonjoonho): Remove duplication in this code fragment.
        if (outputDestination != OutputDestination.DISPLAY && outputFile == null) {
            MessageDialog(
                IJ.getInstance(),
                "Error", "File to save to not specified."
            )
            return
        }

        // We need to create a copy of the image since we want to show the results on the original image, but
        // preprocessing is done in-place which changes the image.
        val originalImage = image.duplicate()
        originalImage.title = "${image.title} - Transduced cells overlapping target cells"

        val channelImages = ChannelSplitter.split(image)
        if (targetChannel < 1 || targetChannel > channelImages.size) {
            MessageDialog(
                IJ.getInstance(),
                "Error", "Target channel selected does not exist. There are %d channels available.".format(channelImages.size)
            )
            return
        }

        if (transducedChannel < 1 || transducedChannel > channelImages.size) {
            MessageDialog(
                IJ.getInstance(),
                "Error", "Tranduced channel selected does not exist. There are %d channels available.".format(channelImages.size)
            )
            return
        }

        val targetImage = channelImages[targetChannel - 1]
        targetImage.show()
        val transducedImage = channelImages[transducedChannel - 1]
        transducedImage.show()

        val targetCells = extractCells(targetImage)
        val transducedCells = extractCells(transducedImage)

        val cellComparator = PixelCellComparator()
        val transductionAnalysis = BucketedNaiveColocalizer(largestCellDiameter.toInt(), targetImage.width, targetImage.height, cellComparator).analyseTransduction(targetCells, transducedCells)
        val intensityAnalysis = cellColocalizationService.analyseCellIntensity(targetImage, transductionAnalysis.overlapping.map { it.toRoi() }.toTypedArray())
        showCount(targetCells=targetCells, transductionAnalysis=transductionAnalysis)

        if (outputDestination == OutputDestination.DISPLAY) {
            ImageJTableColocalizationOutput(intensityAnalysis, uiService).output()
        } else if (outputDestination == OutputDestination.CSV) {
            CSVColocalizationOutput(intensityAnalysis, outputFile!!).output()
        }

        val roiManager = RoiManager.getRoiManager()
        cellColocalizationService.markOverlappingCells(originalImage, roiManager, transductionAnalysis.overlapping.map{x -> x.toRoi()})
        originalImage.show()
    }

    /**
     * Extract an array of cells (as ROIs) from the specified image
     */
    private fun extractCells(image: ImagePlus): List<PositionedCell> {
        // Process the target image.
        cellSegmentationService.preprocessImage(image, largestCellDiameter, gaussianBlurSigma)
        cellSegmentationService.segmentImage(image)

        // Create a unique ROI manager but don't display it.
        // This allows us to retrieve only the ROIs corresponding to this image
        val roiManager = RoiManager(true)
        val cells = cellSegmentationService.identifyCells(roiManager, image)
        return cells.map { roi -> PositionedCell.fromRoi(roi) }
    }

    /**
     * Displays the resulting counts as a results table.
     */
    private fun showCount(
        targetCells: List<PositionedCell>,
        transductionAnalysis: TransductionAnalysis
    ) {
        val table = DefaultGenericTable()
        val targetCellCountColumn = IntColumn()
        val transducedTargetCellCount = IntColumn()
        val transducedNonTargetCellCount = IntColumn()
        targetCellCountColumn.add(targetCells.size)
        transducedTargetCellCount.add(transductionAnalysis.overlapping.size)
        transducedNonTargetCellCount.add(transductionAnalysis.disjoint.size)

        table.add(targetCellCountColumn)
        table.add(transducedTargetCellCount)
        table.add(transducedNonTargetCellCount)
        table.setColumnHeader(0, "Target Cell Count")
        table.setColumnHeader(1, "Transduced Target Cell Count")
        table.setColumnHeader(2, "Transduced Non-Target Cell Count")
        uiService.show(table)
    }

    companion object {
        /**
         * Entry point to directly open the plugin, used for debugging purposes.
         *
         * @throws Exception
         */
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val ij = ImageJ()

            ij.context().inject(CellSegmentationService())
            ij.context().inject(CellColocalizationService())

            ij.launch()

            val file: File = ij.ui().chooseFile(null, "open")
            val imp = IJ.openImage(file.path)
            imp.show()
            ij.command().run(SimpleColocalization::class.java, true)
        }
    }
}
