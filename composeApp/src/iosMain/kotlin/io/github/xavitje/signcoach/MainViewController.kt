package io.github.xavitje.signcoach

import androidx.compose.ui.window.ComposeUIViewController
import io.github.xavitje.signcoach.presentation.StudentIdleGuard
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

private var lifecycleObserversInstalled = false

private fun installLifecycleObservers() {
    if (lifecycleObserversInstalled) return
    lifecycleObserversInstalled = true
    val center = NSNotificationCenter.defaultCenter
    center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ ->
        StudentIdleGuard.onAppBackgrounded()
    }
    center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) { _ ->
        StudentIdleGuard.onAppForegrounded()
    }
}

fun MainViewController() = ComposeUIViewController {
    installLifecycleObservers()
    App()
}
