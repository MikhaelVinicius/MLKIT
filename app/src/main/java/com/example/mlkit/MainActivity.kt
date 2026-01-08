package com.example.mlkit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity() {

    // 1. O "Vigia" da Permissão
    // Esse código prepara o app para receber a resposta do usuário (Sim ou Não)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Se o usuário deixou, ligamos a câmera
            iniciarCamera()
        } else {
            // Se ele negou, avisamos que não vai funcionar
            Toast.makeText(this, "Permissão de câmera necessária!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 2. A Verificação de Segurança (A Ignição)
        // Antes de iniciar a câmera, verificamos se já temos permissão
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Já tem permissão? Liga a câmera.
            iniciarCamera()
        } else {
            // Não tem? Pede a permissão (mostra o popup).
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun iniciarCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Tenta obter o provedor da câmera. Se der erro no XML (ID errado), vai cair no catch lá embaixo.
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Configurar o Preview (O que o usuário vê)
                // ATENÇÃO: Verifique se no seu XML o id é viewFinder mesmo!
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
                }

                // Configurar o Analisador (Onde a IA trabalha)
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                            processarImagemDaCamera(imageProxy)
                        }
                    }

                // Ligar tudo ao ciclo de vida
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )

            } catch(exc: Exception) {
                Log.e("ErroCamera", "Falha ao iniciar a câmera ou encontrar a View", exc)
                Toast.makeText(this, "Erro ao iniciar câmera: Verifique os Logs", Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // A anotação abaixo evita erros de compilação sobre APIs experimentais
    @OptIn(ExperimentalGetImage::class)
    private fun processarImagemDaCamera(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Verifique se no XML o id é tvResultado
                    findViewById<TextView>(R.id.tvResultado).text = visionText.text
                }
                .addOnFailureListener { e ->
                    Log.e("ErroML", "Erro ao ler texto", e)
                }
                .addOnCompleteListener {
                    // FECHAR O FRAME É OBRIGATÓRIO
                    imageProxy.close()
                }
        } else {
            // Se a imagem for nula, também precisamos fechar para não travar
            imageProxy.close()
        }
    }
}