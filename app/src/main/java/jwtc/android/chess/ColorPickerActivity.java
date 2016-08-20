package jwtc.android.chess;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SVBar;

/**
 * Created by Profile on 8/7/2016.
 */
public class ColorPickerActivity extends MyBaseActivity implements ColorPicker.OnColorChangedListener {

    private ColorPicker picker;
    ImageView ImageViewlightColor, ImageViewdarkColor, ImageViewselectedColor;
    ColorDrawable square1, square2, square3;
    int choice;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.color_picker_dialog);

        choice = 1;

        ImageViewlightColor = (ImageView) findViewById(R.id.imageViewLightSquares);
        ImageViewdarkColor = (ImageView) findViewById(R.id.imageViewDarkSquares);
        ImageViewselectedColor = (ImageView) findViewById(R.id.imageViewSelected);

        square1 = (ColorDrawable) ImageViewlightColor.getBackground();
        square2 = (ColorDrawable) ImageViewdarkColor.getBackground();
        square3 = (ColorDrawable) ImageViewselectedColor.getBackground();

        SharedPreferences pref = getSharedPreferences("ChessPlayer", Context.MODE_PRIVATE);
        int squarecolor1 = pref.getInt("color1", 0xffff0066);
        int squarecolor2 = pref.getInt("color2", 0xff006600);
        int squarecolor3 = pref.getInt("color3", 0xcc99ccff);

        ImageViewlightColor.setBackgroundColor(squarecolor1);
        ImageViewdarkColor.setBackgroundColor(squarecolor2);
        ImageViewselectedColor.setBackgroundColor(squarecolor3);
        ImageViewlightColor.setImageResource(R.drawable.select_square);


        SVBar svBar = (SVBar) findViewById(R.id.svbar);
        picker = (ColorPicker) findViewById(R.id.picker);
        picker.addSVBar(svBar);
        picker.setShowOldCenterColor(false);
        picker.setColor(squarecolor1);
        picker.setOnColorChangedListener(this);

        // put square select image - among the three squares
        ImageViewlightColor.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                setSelected(ImageViewlightColor);
                choice = 1;
                picker.setColor(square1.getColor());
                return true;
            }
        });
        ImageViewdarkColor.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                setSelected(ImageViewdarkColor);
                choice = 2;
                picker.setColor(square2.getColor());
                return true;
            }
        });
        ImageViewselectedColor.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                setSelected(ImageViewselectedColor);
                choice = 3;
                picker.setColor(square3.getColor());
                return true;
            }
        });


    }

    private void setSelected(ImageView selected) {
        ImageViewlightColor.setImageResource(0);
        ImageViewdarkColor.setImageResource(0);
        ImageViewselectedColor.setImageResource(0);
        selected.setImageResource(R.drawable.select_square);
    }

    @Override
    public void onColorChanged(int color) {
        SharedPreferences pref = getSharedPreferences("ChessPlayer", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        switch (choice) {
            case 1:
                ImageViewlightColor.setBackgroundColor(color);
                editor.putInt("color1", color);
                break;
            case 2:
                ImageViewdarkColor.setBackgroundColor(color);
                editor.putInt("color2", color);
                break;
            case 3:
                ImageViewselectedColor.setBackgroundColor(color);
                editor.putInt("color3", color);
                break;
        }

        editor.apply();
    }
}
