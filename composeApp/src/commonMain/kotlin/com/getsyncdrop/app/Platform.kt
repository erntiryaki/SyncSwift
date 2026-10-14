package com.getsyncdrop.app

enum class PlatformFlavor { Android, Ios }
expect fun currentPlatform(): PlatformFlavor
