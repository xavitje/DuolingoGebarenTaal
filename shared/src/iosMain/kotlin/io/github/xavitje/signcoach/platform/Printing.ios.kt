package io.github.xavitje.signcoach.platform

import platform.UIKit.UIMarkupTextPrintFormatter
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController

actual fun printHtml(jobName: String, html: String): Boolean {
    val controller = UIPrintInteractionController.sharedPrintController()
    val info = UIPrintInfo.printInfo()
    info.jobName = jobName
    info.outputType = UIPrintInfoOutputType.UIPrintInfoOutputGeneral
    controller.printInfo = info
    controller.printFormatter = UIMarkupTextPrintFormatter(markupText = html)
    controller.presentAnimated(true, completionHandler = null)
    return true
}
