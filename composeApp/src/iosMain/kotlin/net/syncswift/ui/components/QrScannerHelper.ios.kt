package net.syncswift.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIDetector
import platform.CoreImage.CIDetectorAccuracyHigh
import platform.CoreImage.CIDetectorTypeQRCode
import platform.CoreImage.CIImage
import platform.CoreImage.CIQRCodeFeature
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private class ImagePickerDelegate(
    private val onImagePicked: (UIImage) -> Unit,
    private val onCancel: () -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        if (image != null) {
            onImagePicked(image)
        } else {
            onCancel()
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
        onCancel()
    }
}

private var activeImagePickerDelegate: ImagePickerDelegate? = null

private class QrCaptureDelegate(
    private val onCodeFound: (String) -> Unit
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    private var isHandled = false

    override fun captureOutput(
        output: platform.AVFoundation.AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: platform.AVFoundation.AVCaptureConnection
    ) {
        if (isHandled) return
        val firstObj = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject ?: return
        val strValue = firstObj.stringValue
        if (!strValue.isNullOrBlank()) {
            isHandled = true
            dispatch_async(dispatch_get_main_queue()) {
                onCodeFound(strValue)
            }
        }
    }
}

private var activeCaptureDelegate: QrCaptureDelegate? = null

@OptIn(ExperimentalForeignApi::class)
private class CustomScannerViewController(
    private val captureSession: AVCaptureSession
) : UIViewController(nibName = null, bundle = null) {

    var cameraContainerView: UIView? = null
    var previewLayer: AVCaptureVideoPreviewLayer? = null
    var captureDelegate: QrCaptureDelegate? = null

    @kotlinx.cinterop.ObjCAction
    fun dismissScanner() {
        try {
            captureSession.stopRunning()
        } catch (_: Exception) {}
        activeCaptureDelegate = null
        captureDelegate = null
        dismissViewControllerAnimated(true, null)
    }

    @kotlinx.cinterop.ObjCAction
    fun onBackgroundTapped(gesture: UITapGestureRecognizer) {
        val container = cameraContainerView ?: return
        val locX = gesture.locationInView(view).useContents { x.toDouble() }
        val locY = gesture.locationInView(view).useContents { y.toDouble() }
        val frameX = container.frame.useContents { origin.x.toDouble() }
        val frameY = container.frame.useContents { origin.y.toDouble() }
        val frameW = container.frame.useContents { size.width.toDouble() }
        val frameH = container.frame.useContents { size.height.toDouble() }

        val isInside = locX >= frameX && locX <= (frameX + frameW) &&
                locY >= frameY && locY <= (frameY + frameH)
        if (!isInside) {
            dismissScanner()
        }
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        val container = cameraContainerView ?: return
        previewLayer?.setFrame(container.bounds)
    }
}

actual object QrScannerHelper {
    actual fun isAvailable(): Boolean = true
}

@Composable
actual fun rememberQrScanner(
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    return remember(onSuccess, onError) {
        {
            val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
            when (status) {
                AVAuthorizationStatusNotDetermined -> {
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                        dispatch_async(dispatch_get_main_queue()) {
                            if (granted) {
                                presentNativeScanner(onSuccess, onError)
                            } else {
                                onError("Kamera izni verilmedi.")
                            }
                        }
                    }
                }
                AVAuthorizationStatusAuthorized -> {
                    presentNativeScanner(onSuccess, onError)
                }
                else -> {
                    onError("Kamera izni verilmedi. Ayarlardan izin verin.")
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun presentNativeScanner(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
    try {
        val captureSession = AVCaptureSession()
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device == null) {
            onError("Kamera bulunamadı.")
            return
        }

        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
        if (input != null && captureSession.canAddInput(input)) {
            captureSession.addInput(input)
        } else {
            onError("Kamera başlatılamadı.")
            return
        }

        val scannerVc = CustomScannerViewController(captureSession)
        scannerVc.modalPresentationStyle = UIModalPresentationOverFullScreen
        scannerVc.modalTransitionStyle = UIModalTransitionStyleCrossDissolve

        val captureDelegate = QrCaptureDelegate { qrText ->
            try {
                captureSession.stopRunning()
            } catch (_: Exception) {}
            activeCaptureDelegate = null
            scannerVc.captureDelegate = null
            scannerVc.dismissViewControllerAnimated(true) {
                onSuccess(qrText)
            }
        }
        activeCaptureDelegate = captureDelegate
        scannerVc.captureDelegate = captureDelegate

        val output = AVCaptureMetadataOutput()
        if (captureSession.canAddOutput(output)) {
            captureSession.addOutput(output)
            output.setMetadataObjectsDelegate(captureDelegate, dispatch_get_main_queue())
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        }

        // Screen dimensions (explicit Double)
        val screenBounds = UIScreen.mainScreen.bounds
        val screenWidth: Double = screenBounds.useContents { size.width.toDouble() }
        val screenHeight: Double = screenBounds.useContents { size.height.toDouble() }

        // Dark translucent background (#730B101B)
        scannerVc.view.backgroundColor = UIColor.colorWithRed(11.0 / 255.0, 16.0 / 255.0, 27.0 / 255.0, 0.85)

        // Native blur effect
        val blurEffect = UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleDark)
        val blurView = UIVisualEffectView(effect = blurEffect)
        blurView.setFrame(scannerVc.view.bounds)
        blurView.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        scannerVc.view.addSubview(blurView)

        // Center camera container (260x260 dp square)
        val boxSize: Double = if (screenWidth - 48.0 < 260.0) screenWidth - 48.0 else 260.0
        val boxX: Double = (screenWidth - boxSize) / 2.0
        val boxY: Double = (screenHeight - boxSize) / 2.0 - 40.0

        val cameraContainer = UIView(frame = platform.CoreGraphics.CGRectMake(boxX, boxY, boxSize, boxSize))
        cameraContainer.layer.cornerRadius = 24.0
        cameraContainer.layer.masksToBounds = true
        cameraContainer.backgroundColor = UIColor.blackColor

        val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession)
        previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        previewLayer.setFrame(cameraContainer.bounds)
        cameraContainer.layer.addSublayer(previewLayer)
        scannerVc.view.addSubview(cameraContainer)

        scannerVc.cameraContainerView = cameraContainer
        scannerVc.previewLayer = previewLayer

        // Frame border overlay with turquoise stroke (#38D5E2)
        val borderOverlay = UIView(frame = platform.CoreGraphics.CGRectMake(boxX, boxY, boxSize, boxSize))
        borderOverlay.backgroundColor = UIColor.clearColor
        borderOverlay.layer.cornerRadius = 24.0
        borderOverlay.layer.borderWidth = 2.5
        borderOverlay.layer.borderColor = UIColor.colorWithRed(56.0 / 255.0, 213.0 / 255.0, 226.0 / 255.0, 1.0).CGColor
        borderOverlay.userInteractionEnabled = false
        scannerVc.view.addSubview(borderOverlay)

        // SyncSwift white logo below the scanner
        val logoWidth: Double = if (screenWidth - 40.0 < 300.0) screenWidth - 40.0 else 300.0
        val logoHeight: Double = 70.0
        val logoX: Double = (screenWidth - logoWidth) / 2.0
        val logoY: Double = boxY + boxSize + 28.0

        val logoView = UIImageView(frame = platform.CoreGraphics.CGRectMake(logoX, logoY, logoWidth, logoHeight))
        logoView.contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
        val logoImage = UIImage.imageNamed("logo_syncswift")
            ?: NSBundle.mainBundle.pathForResource("logo_syncswift", "png")?.let { UIImage.imageWithContentsOfFile(it) }

        if (logoImage != null) {
            logoView.image = logoImage.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate)
            logoView.tintColor = UIColor.whiteColor
        }
        scannerVc.view.addSubview(logoView)

        // Circular top-left back button (#1A2436 background with #28364D border)
        val closeButton = UIButton.buttonWithType(UIButtonTypeCustom)
        closeButton.setFrame(platform.CoreGraphics.CGRectMake(20.0, 50.0, 38.0, 38.0))
        closeButton.layer.cornerRadius = 19.0
        closeButton.layer.masksToBounds = true
        closeButton.backgroundColor = UIColor.colorWithRed(26.0 / 255.0, 36.0 / 255.0, 54.0 / 255.0, 0.95)
        closeButton.layer.borderWidth = 1.0
        closeButton.layer.borderColor = UIColor.colorWithRed(40.0 / 255.0, 54.0 / 255.0, 77.0 / 255.0, 1.0).CGColor

        val backIcon = UIImage.systemImageNamed("chevron.left")
        if (backIcon != null) {
            closeButton.setImage(backIcon, forState = UIControlStateNormal)
            closeButton.tintColor = UIColor.whiteColor
        } else {
            closeButton.setTitle("✕", forState = UIControlStateNormal)
            closeButton.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        }
        closeButton.addTarget(
            scannerVc,
            platform.objc.sel_registerName("dismissScanner"),
            UIControlEventTouchUpInside
        )
        scannerVc.view.addSubview(closeButton)

        // Tap on background outside to dismiss
        val bgTapGesture = UITapGestureRecognizer(target = scannerVc, action = platform.objc.sel_registerName("onBackgroundTapped:"))
        scannerVc.view.addGestureRecognizer(bgTapGesture)

        val rootVc = findRootViewController()
        rootVc?.presentViewController(scannerVc, animated = true) {
            captureSession.startRunning()
        }
    } catch (e: Exception) {
        onError(e.message ?: "Kamera açılamadı")
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberQrImagePicker(
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    return remember(onSuccess, onError) {
        {
            val rootVc = findRootViewController()
            if (rootVc == null) {
                onError("Ekran bulunamadı")
                return@remember
            }

            val picker = UIImagePickerController()
            picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary

            val delegate = ImagePickerDelegate(
                onImagePicked = { image ->
                    try {
                        val cgImage = image.CGImage
                        if (cgImage == null) {
                            onError("Görsel okunamadı.")
                            return@ImagePickerDelegate
                        }
                        val ciImage = CIImage.imageWithCGImage(cgImage)
                        val detector = CIDetector.detectorOfType(
                            CIDetectorTypeQRCode,
                            context = null,
                            options = mapOf(CIDetectorAccuracyHigh to CIDetectorAccuracyHigh)
                        )
                        val features = detector?.featuresInImage(ciImage) ?: emptyList<Any?>()
                        val qrFeature = features.firstOrNull() as? CIQRCodeFeature
                        if (qrFeature?.messageString != null) {
                            onSuccess(qrFeature.messageString!!)
                        } else {
                            onError("Görselde QR kod bulunamadı.")
                        }
                    } catch (e: Exception) {
                        onError("QR okunamadı: ${e.message}")
                    }
                },
                onCancel = {}
            )
            activeImagePickerDelegate = delegate
            picker.delegate = delegate
            rootVc.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private fun findRootViewController(): UIViewController? {
    val scenes = UIApplication.sharedApplication.connectedScenes
    for (scene in scenes) {
        val windowScene = scene as? UIWindowScene ?: continue
        val win = windowScene.windows.mapNotNull { it as? UIWindow }.firstOrNull { it.isKeyWindow() }
            ?: windowScene.windows.firstOrNull() as? UIWindow
        if (win?.rootViewController != null) {
            var top = win.rootViewController
            while (top?.presentedViewController != null) {
                top = top.presentedViewController
            }
            return top
        }
    }
    return (UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow)?.rootViewController
}
