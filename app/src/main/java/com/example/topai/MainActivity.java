package com.example.topai;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private Button speakButton, imageProcessButton, chatbotSendButton;
    private EditText chatbotInput;
    private TextView chatbotResponse;
    private ImageView imageView;
    private Retrofit retrofit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        speakButton = findViewById(R.id.btnSpeak);
        imageProcessButton = findViewById(R.id.btnProcessImage);
        chatbotSendButton = findViewById(R.id.btnSendChat);
        chatbotInput = findViewById(R.id.editTextChat);
        chatbotResponse = findViewById(R.id.textViewChatResponse);
        imageView = findViewById(R.id.imageView);

        // Initialize Text-to-Speech
        tts = new TextToSpeech(this, this);

        // Initialize Retrofit for API calls (DuckDuckGo)
        retrofit = new Retrofit.Builder()
                .baseUrl("https://api.duckduckgo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // Set up button click listeners
        speakButton.setOnClickListener(v -> speakOut("Hello, this is your screen reader!"));
        imageProcessButton.setOnClickListener(v -> processImage());
        chatbotSendButton.setOnClickListener(v -> {
            String message = chatbotInput.getText().toString();
            sendChatMessage(message);
        });

        // Start the floating overlay service if permission granted
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivity(intent);
        } else {
            startService(new Intent(this, FloatingWidgetService.class));
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
        }
    }

    private void speakOut(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void processImage() {
        // Load sample image from drawable
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sample_image);
        imageView.setImageBitmap(bitmap);

        // Convert bitmap to ML Kit InputImage
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        com.google.mlkit.vision.label.ImageLabeler labeler =
                ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);

        labeler.process(image)
                .addOnSuccessListener(labels -> {
                    StringBuilder resultText = new StringBuilder("Image Labels:\n");
                    for (ImageLabel label : labels) {
                        resultText.append(label.getText())
                                  .append(" (")
                                  .append((int)(label.getConfidence() * 100))
                                  .append("%)\n");
                    }
                    speakOut(resultText.toString());
                    Log.d("MLKit", resultText.toString());
                    updateOverlay(resultText.toString());
                })
                .addOnFailureListener(e -> {
                    Log.e("MLKit", "Error: " + e.getMessage());
                });
    }

    private void sendChatMessage(String message) {
        DuckDuckGoApi api = retrofit.create(DuckDuckGoApi.class);
        api.getInstantAnswer(message, "json").enqueue(new Callback<DuckDuckGoResponse>() {
            @Override
            public void onResponse(Call<DuckDuckGoResponse> call, Response<DuckDuckGoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String reply = response.body().AbstractText;
                    chatbotResponse.setText(reply);
                    speakOut(reply);
                    updateOverlay(reply);
                } else {
                    chatbotResponse.setText("Error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<DuckDuckGoResponse> call, Throwable t) {
                chatbotResponse.setText("Network Error: " + t.getMessage());
            }
        });
    }

    private void updateOverlay(String text) {
        // Call a static method from FloatingWidgetService to update overlay text
        FloatingWidgetService.updateOverlayText(this, text);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
