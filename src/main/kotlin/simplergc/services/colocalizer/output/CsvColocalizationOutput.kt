package simplergc.services.colocalizer.output

import java.io.File
import java.io.IOException
import simplergc.services.Parameters
import simplergc.services.Table

/**
 * Displays a table for a transduction analysis with the result of
 * overlapping, transduced cells.
 */
class CsvColocalizationOutput(private val transductionParameters: Parameters.TransductionParameters) :
    ColocalizationOutput() {

    val outputPath: String = "${transductionParameters.outputFile.path}${File.separator}"

    override fun output() {
        createOutputFolder()
        writeDocumentationCsv()
        writeSummaryCsv()
        writeTransductionAnalysisCsv()
        writeParametersCsv()
    }

    fun createOutputFolder() {
        val outputFileSuccess = File(transductionParameters.outputFile.path).mkdir()
        // If the output file cannot be created, an IOException should be caught
        if (!outputFileSuccess and !transductionParameters.outputFile.exists()) {
            throw IOException()
        }
    }

    val documentationCsv = Table(arrayOf())

    private fun writeDocumentationCsv() {
        // Constant array of information
        documentationCsv.addRow(
            DocumentationRow(
                "The Article: ",
                "TODO: insert full citation of manuscript when complete"
            )
        )
        documentationCsv.addRow(DocumentationRow("", ""))
        documentationCsv.addRow(DocumentationRow("Abbreviation: ", "Description"))
        documentationCsv.addRow(DocumentationRow("Summary: ", "Key overall measurements per image"))
        documentationCsv.addRow(
            DocumentationRow(
                "Transduced Cell Analysis: ",
                "Cell-by-cell metrics of transduced cells"
            )
        )
        documentationCsv.addRow(DocumentationRow("Parameters: ", "Parameters used for SimpleRGC plugin"))
        documentationCsv.produceCsv(File("${outputPath}Documentation.csv"))
    }

    fun writeSummaryCsv() {
        // TODO (#156): Add integrated density
        for ((fileName, result) in fileNameAndResultsList) {
            summaryData.addRow(SummaryRow(fileName = fileName, summary = result.getSummary()))
        }
        summaryData.produceCsv(File("${outputPath}Summary.csv"))
    }

    private fun writeTransductionAnalysisCsv() {
        for ((fileName, result) in fileNameAndResultsList) {
            result.overlappingTransducedIntensityAnalysis.forEach { cellAnalysis ->
                transductionAnalysisData.addRow(TransductionAnalysisRow(fileName, cellAnalysis))
            }
        }
        transductionAnalysisData.produceCsv(File("${outputPath}Transduced Cell Analysis.csv"))
    }

    fun writeParametersCsv() {
        // TODO (#156): Add pixel size (micrometers) in next sprint.
        for ((fileName, _) in fileNameAndResultsList) {
            parametersData.addRow(
                ParametersRow(
                    fileName = fileName,
                    morphologyChannel = transductionParameters.targetChannel,
                    excludeAxonsFromMorphologyChannel = transductionParameters.shouldRemoveAxonsFromTargetChannel,
                    transductionChannel = transductionParameters.transducedChannel,
                    excludeAxonsFromTransductionChannel = transductionParameters.shouldRemoveAxonsFromTransductionChannel,
                    cellDiameterText = transductionParameters.cellDiameterText,
                    localThresholdRadius = transductionParameters.localThresholdRadius,
                    gaussianBlurSigma = transductionParameters.gaussianBlurSigma
                )
            )
        }
        parametersData.produceCsv(File("${outputPath}Parameters.csv"))
    }
}