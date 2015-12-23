package jwtc.android.chess;

/**
 * Created by Tim on 12/19/2015.
 */
import android.app.Activity;
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
    private final Integer[] imageId;
    public start_CustomList(Activity context,
                      String[] title, Integer[] imageId) {
        super(context, R.layout.start_list_single, title);
        this.context = context;
        this.title = title;
        this.imageId = imageId;

    }
    @Override
    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View rowView= inflater.inflate(R.layout.start_list_single, null, true);
        TextView txtTitle = (TextView) rowView.findViewById(R.id.txt);

        ImageView imageView = (ImageView) rowView.findViewById(R.id.img);

        txtTitle.setText(title[position]);

        imageView.setImageResource(imageId[position]);
        if(imageView != null) {
            imageView.startAnimation(AnimationUtils.loadAnimation(this.context, R.anim.pulse));
        }
        return rowView;
    }
}
