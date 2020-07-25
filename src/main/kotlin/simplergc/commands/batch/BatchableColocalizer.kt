package simplergc.commands.batch

import ij.IJ
import ij.ImagePlus
import ij.gui.MessageDialog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.scijava.Context
import simplergc.commands.ChannelDoesNotExistException
import simplergc.commands.RGCTransduction
import simplergc.commands.RGCTransduction.TransductionResult
import simplergc.commands.batch.RGCBatch.OutputFormat
import simplergc.services.CellDiameterRange
import simplergc.services.Parameters
import simplergc.services.batch.output.BatchCsvColocalizationOutput
import simplergc.services.batch.output.BatchXlsxColocalizationOutput
import java.io.File

class BatchableColocalizer(
    private val targetChannel: Int,
    private val shouldRemoveAxonsFromTargetChannel: Boolean,
    private val transducedChannel: Int,
    private val shouldRemoveAxonsFromTransductionChannel: Boolean,
    private val context: Context
) : Batchable {
    override fun process(
        inputImages: List<ImagePlus>,
        cellDiameterRange: CellDiameterRange,
        localThresholdRadius: Int,
        gaussianBlurSigma: Double,
        outputFormat: String,
        outputFile: File
    ) {
        val rgcTransduction = RGCTransduction()

        rgcTransduction.localThresholdRadius = localThresholdRadius
        rgcTransduction.targetChannel = targetChannel
        rgcTransduction.shouldRemoveAxonsFromTargetChannel = shouldRemoveAxonsFromTargetChannel
        rgcTransduction.transducedChannel = transducedChannel
        rgcTransduction.shouldRemoveAxonsFromTransductionChannel = shouldRemoveAxonsFromTransductionChannel
        context.inject(rgcTransduction)

        val fileNameAndAnalysis = mutableListOf<Pair<String, TransductionResult>>()

        val deferred = inputImages.map { image ->
            GlobalScope.async {
                try {
                    return@async rgcTransduction.process(image, cellDiameterRange)
                } catch (e: ChannelDoesNotExistException) {
                    MessageDialog(IJ.getInstance(), "Error", e.message)
                    return@async null
                }
            }
        }

        var results = listOf<TransductionResult?>()
        runBlocking {
            results = deferred.map { it.await() }
        }
        for ((i, image) in inputImages.withIndex()) {
            val v = results[i]
            if (v != null) {
                fileNameAndAnalysis.add(Pair(image.title, v))
            }
        }

        val transductionParameters = Parameters.Transduction(
            outputFile,
            shouldRemoveAxonsFromTargetChannel,
            transducedChannel,
            shouldRemoveAxonsFromTransductionChannel,
            cellDiameterRange.toString(),
            localThresholdRadius,
            gaussianBlurSigma,
            targetChannel
        )

        writeOutput(fileNameAndAnalysis, transductionParameters, outputFormat)
    }

    private fun writeOutput(
        fileNameAndAnalysis: List<Pair<String, TransductionResult>>,
        transductionParameters: Parameters.Transduction,
        outputFormat: String
    ) {
        val output = when (outputFormat) {
            OutputFormat.XLSX -> BatchXlsxColocalizationOutput(transductionParameters)
            OutputFormat.CSV -> BatchCsvColocalizationOutput(transductionParameters)
            else -> throw IllegalArgumentException("Invalid output type provided: $outputFormat")
        }

        fileNameAndAnalysis.forEach { output.addTransductionResultForFile(it.second, it.first) }

        output.output()
    }
}
