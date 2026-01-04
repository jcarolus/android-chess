package jwtc.android.chess;

import android.os.Bundle;
import android.widget.ListView;

import jwtc.android.chess.activities.StartBaseActivity;
import jwtc.android.chess.helpers.StartMenuAdapter;

public class start extends StartBaseActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        layoutResource = R.layout.start_foss;

        super.onCreate(savedInstanceState);

        //ListView listView = findViewById(R.id.ListStart);
        //String[] menuItems = getResources().getStringArray(R.array.start_menu);
        //StartMenuAdapter adapter = new StartMenuAdapter(this, menuItems);
        //listView.setAdapter(adapter);
    }
}
