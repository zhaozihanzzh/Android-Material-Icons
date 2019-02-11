package com.majeur.materialicons;

import android.app.*;
import android.content.res.*;
import android.graphics.*;
import android.os.*;
import android.support.annotation.*;
import android.support.v7.app.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import net.rdrei.android.dirchooser.*;

import android.support.v7.app.AlertDialog;

public class ExportActivity extends ActionBarActivity implements DirectoryChooserFragment.OnFragmentInteractionListener {

    public static final String EXTRA_SELECTED_ITEMS = "selected_items";

    private static final int[] CHECKBOX_IDS = {R.id.checkbox_ldpi,
            R.id.checkbox_mdpi, R.id.checkbox_hdpi, R.id.checkbox_xhdpi,
            R.id.checkbox_xxhdpi, R.id.checkbox_xxxhdpi};

    private List<String> mAssetsFiles = new ArrayList<>();
    private int mColor = Color.DKGRAY;

    private View mColorPreview;
    private TextView mPathTextView;
    private DirectoryChooserFragment mDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mAssetsFiles.addAll(Arrays.asList(getIntent().getStringArrayExtra(EXTRA_SELECTED_ITEMS)));

        View colorButton = findViewById(R.id.color_button);
        colorButton.setOnClickListener(mColorClickListener);
        mColorPreview = findViewById(R.id.color_preview);
        mColorPreview.setBackgroundColor(mColor);

        View pathButton = findViewById(R.id.path_button);
        pathButton.setOnClickListener(mPathClickListener);
        mPathTextView = (TextView) findViewById(R.id.path_text);
        mPathTextView.setText(Environment.getExternalStorageDirectory().getPath());

        ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setOnItemClickListener(mOnItemClickListener);
        listView.setOnItemLongClickListener(mOnItemLongClickListener);
        listView.setAdapter(mListAdapter);

        findViewById(R.id.start_export_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startExport();
            }
        });

        mDialog = DirectoryChooserFragment.newInstance(getString(R.string.app_name), null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Toast.makeText(ExportActivity.this, R.string.long_press_to_remove, Toast.LENGTH_SHORT).show();
        }
    };

    private AdapterView.OnItemLongClickListener mOnItemLongClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            Toast.makeText(ExportActivity.this,
                    Utils.svgFileNameToLabel(mAssetsFiles.remove(position)) + " " + getString(R.string.removed),
                    Toast.LENGTH_SHORT)
                    .show();
	   
            mListAdapter.notifyDataSetChanged();
            return true;
        }
    };

    private View.OnClickListener mColorClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            TypedArray typedArray = getResources().obtainTypedArray(R.array.mtrl_colors);
            final int[] colors = new int[typedArray.length()];
            for (int i = 0; i < typedArray.length(); i++)
                colors[i] = typedArray.getColor(i, Color.BLUE);
            typedArray.recycle();

            GridView gridView = new GridView(ExportActivity.this);
            gridView.setNumColumns(getResources().getInteger(R.integer.color_grid_num_col));
            gridView.setGravity(Gravity.CENTER);
            gridView.setAdapter(new SimpleAdapter() {
                @Override
                public int getCount() {
                    return colors.length;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    if (convertView == null)
                        convertView = new RoundView(parent.getContext());

                    ((RoundView) convertView).setRoundColor(colors[position]);
                    return convertView;
                }
            });

            final AlertDialog dialog = new AlertDialog.Builder(ExportActivity.this)
                    .setTitle(R.string.material_colors)
                    .setView(gridView)
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();

            gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    dialog.dismiss();
                    mColor = colors[position];
                    mColorPreview.setBackgroundColor(mColor);
                }
            });
        }
    };

    private View.OnClickListener mPathClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mDialog.show(getSupportFragmentManager(), null);
        }
    };

    @Override
    public void onSelectDirectory(@NonNull String s) {
        mPathTextView.setText(s);
        mDialog.dismiss();
    }

    @Override
    public void onCancelChooser() {
        mDialog.dismiss();
    }

    private void startExport() {
        List<Density> desiredDensities = new ArrayList<>(CHECKBOX_IDS.length);
        final Density[] densities = Density.values();
        for (int i = 0; i < CHECKBOX_IDS.length; i++) {
            CheckBox checkBox = (CheckBox) findViewById(CHECKBOX_IDS[i]);
            if (checkBox.isChecked())
                desiredDensities.add(densities[i]);
        }

        if (desiredDensities.size() == 0) {
            Toast.makeText(this, R.string.no_density_selected, Toast.LENGTH_LONG).show();//TODO
            return;
        }

        AsyncExporter.Params params = new AsyncExporter.Params();
        params.desiredColor = mColor;
        params.desiredDensities = desiredDensities;
        params.desiredFiles = mAssetsFiles;
        params.path = mPathTextView.getText().toString();
        params.saveType = getSaveType();

        final ProgressDialog dialog = new ProgressDialog(this);
                dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                dialog.setProgress(100);
                

        new AsyncExporter(this, new AsyncExporter.ExportStateCallbacks() {
            @Override
            public void onPreExport() {
                dialog.show();
            }

            @Override
            public void onExportProgressUpdate(AsyncExporter.Progress progress) {
                // dialog.setMaxProgress(progress.totalProgress);
                dialog.setProgress((int)(((float)progress.currentProgress/(float)progress.totalProgress)*100));
                dialog.setMessage(progress.currentDensity + "\n" + progress.currentFileName);
            }

            @Override
            public void onPostExport(final File resultDirectory) {
                dialog.dismiss();
                new AlertDialog.Builder(ExportActivity.this)
                        .setTitle(R.string.success)
                        .setMessage(R.string.icons_exported_correctly)
                        .setNegativeButton(android.R.string.ok, null)
                        .show();
            }
        }).execute(params);
    }


    private AsyncExporter.SaveType getSaveType() {
        RadioButton zipRadio = (RadioButton) findViewById(R.id.radio_zip);
        if (zipRadio.isChecked())
            return AsyncExporter.SaveType.ZIP;
        else
            return AsyncExporter.SaveType.DIR;
    }

    private BaseAdapter mListAdapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return mAssetsFiles.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        class Holder {
            ImageView imageView;
            TextView textView;

            public Holder(View view) {
                imageView = (ImageView) view.findViewById(R.id.image1);
                if (Build.VERSION.SDK_INT > 10) imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                textView = (TextView) view.findViewById(R.id.text1);
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder holder;
            if (convertView == null) {
                convertView = ExportActivity.this.getLayoutInflater().inflate(R.layout.list_item, parent, false);
                holder = new Holder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (Holder) convertView.getTag();
            }

            String fileName = mAssetsFiles.get(position);
            holder.imageView.setImageDrawable(Utils.getDrawableForSvg(ExportActivity.this, fileName));
            holder.textView.setText(Utils.svgFileNameToLabel(fileName));

            return convertView;
        }
    };
}
