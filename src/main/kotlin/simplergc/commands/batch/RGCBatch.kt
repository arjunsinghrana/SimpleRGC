package simplergc.commands.batch

import java.awt.BorderLayout
import java.awt.Dimension
import java.util.prefs.Preferences
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JTabbedPane
import net.imagej.ImageJ
import org.scijava.Context
import org.scijava.app.StatusService
import org.scijava.command.Command
import org.scijava.log.LogService
import org.scijava.plugin.Parameter
import org.scijava.plugin.Plugin
import simplergc.commands.batch.controllers.RGCCounterController
import simplergc.commands.batch.controllers.RGCTransductionController
import simplergc.commands.batch.models.RGCCounterModel
import simplergc.commands.batch.models.RGCTransductionModel
import simplergc.commands.batch.views.RGCCounterView
import simplergc.commands.batch.views.RGCTransductionView
import simplergc.services.CellSegmentationService

@Plugin(type = Command::class, menuPath = "Plugins > Simple RGC > RGC Batch")
class RGCBatch : Command {

    @Parameter
    private lateinit var logService: LogService

    @Parameter
    private lateinit var context: Context

    @Parameter
    private lateinit var statusService: StatusService

    private val prefs = Preferences.userRoot().node(this.javaClass.name)

    object OutputFormat {
        const val XLSX = "XLSX"
        const val CSV = "CSV"
    }

    private fun gui() {
        val frame = JFrame()

        val counterModel = RGCCounterModel(context, prefs)
        val counterView = RGCCounterView(frame, counterModel)
        RGCCounterController(counterView, counterModel, statusService)

        val transductionModel = RGCTransductionModel(context, prefs)
        val transductionView = RGCTransductionView(frame, transductionModel)
        RGCTransductionController(transductionView, transductionModel, statusService)

        val tp = JTabbedPane()
        tp.add("RGC Counter", counterView)
        tp.add("RGC Transduction", transductionView)
        tp.border = BorderFactory.createEmptyBorder(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE)
        frame.add(tp, BorderLayout.CENTER)
        frame.preferredSize = Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT)

        frame.pack()
        frame.isVisible = true
    }

    override fun run() {
        gui()
    }

    companion object {

        // Size constants for RGC Batch JFrame
        private const val BORDER_SIZE = 5
        private const val PREFERRED_WIDTH = 600
        private const val PREFERRED_HEIGHT = 600

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
            ij.launch()

            ij.command().run(RGCBatch::class.java, true)
        }
    }
}
