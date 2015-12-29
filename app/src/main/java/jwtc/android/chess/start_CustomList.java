package jwtc.android.chess;

/**
 * Created by Tim on 12/19/2015.
 */
import android.app.Activity;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class start_CustomList extends ArrayAdapter<String>{

    private final Activity context;
    private final String[] title;

    SharedPreferences myPrefs = getContext().getSharedPreferences("ChessPlayer", getContext().MODE_PRIVATE);
    boolean _bHeart     = myPrefs.getBoolean("bHeart", true);

    public start_CustomList(Activity context,
                  String[] title) {
    super(context, R.layout.start_list_single, title);
    this.context = context;
    this.title = title;

    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View rowView= inflater.inflate(R.layout.start_list_single, null, true);
        TextView txtTitle = (TextView) rowView.findViewById(R.id.txt);

        ImageView imageView = (ImageView) rowView.findViewById(R.id.img);

        txtTitle.setText(title[position]);

        if(imageView != null && position == 5 && _bHeart) {
            imageView.setImageResource(R.drawable.heart);
            imageView.startAnimation(AnimationUtils.loadAnimation(this.context, R.anim.pulse));
        }
        return rowView;
    }
}
