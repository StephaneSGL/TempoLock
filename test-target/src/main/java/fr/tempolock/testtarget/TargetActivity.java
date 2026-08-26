package fr.tempolock.testtarget;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class TargetActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(48, 48, 48, 48);
        layout.setBackgroundColor(Color.rgb(244, 246, 242));

        TextView title = new TextView(this);
        title.setText("Cible TempoLock");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(19, 32, 25));
        title.setGravity(Gravity.CENTER);

        TextView explanation = new TextView(this);
        explanation.setText("Cette application ne contient aucune donnée réelle. Elle sert uniquement aux tests de suspension.");
        explanation.setTextSize(17);
        explanation.setTextColor(Color.rgb(60, 76, 67));
        explanation.setGravity(Gravity.CENTER);
        explanation.setPadding(0, 28, 0, 0);

        layout.addView(title);
        layout.addView(explanation);
        setContentView(layout);
    }
}
