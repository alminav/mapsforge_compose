package com.almica.mapsforge_compose

import timber.log.Timber.DebugTree


class TimberDebugTree : DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String {
        return String.format(
            "[L:%s] [M:%s] [C:%s]",
            element.lineNumber,
            element.methodName,
            super.createStackElementTag(element)
        )
    }
}