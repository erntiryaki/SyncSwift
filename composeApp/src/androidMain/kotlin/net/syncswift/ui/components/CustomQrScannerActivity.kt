package net.syncswift.ui.components

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import net.syncswift.R
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class CustomQrScannerActivity : CaptureActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Native hardware background blur behind translucent window on Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = 35
            }
        }
    }

    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_custom_qr_scanner)

        // Close button
        findViewById<View>(R.id.btn_close_scanner)?.setOnClickListener {
            finish()
        }

        // Tap on transparent background outside to dismiss
        findViewById<View>(R.id.root_scanner_layout)?.setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.center_scanner_content)?.setOnClickListener {
            // consume clicks on scanner container
        }

        val barcodeScannerView = findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)
        // Hide default status text and default viewfinder mask so the square framed camera shows cleanly
        barcodeScannerView.statusView?.visibility = View.GONE
        barcodeScannerView.viewFinder?.visibility = View.GONE

        return barcodeScannerView
    }
}
