package net.syncswift.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
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
    override fun captureOutput(
        output: platform.AVFoundation.AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: platform.AVFoundation.AVCaptureConnection
    ) {
        val firstObj = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject ?: return
        val strValue = firstObj.stringValue
        if (!strValue.isNullOrBlank()) {
            dispatch_async(dispatch_get_main_queue()) {
                onCodeFound(strValue)
            }
        }
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

        val captureDelegate = QrCaptureDelegate { qrText ->
            captureSession.stopRunning()
            findRootViewController()?.dismissViewControllerAnimated(true, null)
            onSuccess(qrText)
        }

        val output = AVCaptureMetadataOutput()
        if (captureSession.canAddOutput(output)) {
            captureSession.addOutput(output)
            output.setMetadataObjectsDelegate(captureDelegate, dispatch_get_main_queue())
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        }

        val scannerVc = UIViewController()
        scannerVc.view.backgroundColor = UIColor.blackColor

        val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession)
        previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        scannerVc.view.layer.addSublayer(previewLayer)

        val closeButton = UIButton.buttonWithType(UIButtonTypeSystem)
        closeButton.setTitle("Kapat", forState = UIControlStateNormal)
        closeButton.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        closeButton.setFrame(CGRectMake(20.0, 50.0, 80.0, 40.0))
        closeButton.addTarget(
            scannerVc,
            platform.objc.sel_registerName("dismissScanner"),
            UIControlEventTouchUpInside
        )
        scannerVc.view.addSubview(closeButton)

        val rootVc = findRootViewController()
        rootVc?.presentViewController(scannerVc, animated = true) {
            previewLayer.setFrame(scannerVc.view.bounds)
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
