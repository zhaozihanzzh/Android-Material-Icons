package com.majeur.materialicons;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.util.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/**
 * This class is used to retrieve icons and keep local copies up to date.
 * On the server side, there is two manifests, main.manifest which contains the count
 * of available icons on the server, and files.manifest which contains the name of each files.
 * These manifest are separated to allow us to compare available items count without downloading
 * all file list (up to 1000 items), thus we use a minimal amount of the user connection data.
 * <p/>
 * On the server files are in the same folder, to get download url, we just need the file name, then
 * we format it with the "folder" url.
 */
public class AsyncDataRetriever extends AsyncTaskICS<Void, String, AsyncDataRetriever.Result> {

    private static final String TAG = "DataAsyncTask";

    private static final String MANIFEST_MAIN_URL = "http://majeurandroid.github.io/Android-Material-Icons/main.manifest";
    private static final String MANIFEST_FILES_URL = "http://majeurandroid.github.io/Android-Material-Icons/files.manifest";
    private static final String FILES_URL_UNFORMATTED = "http://majeurandroid.github.io/Android-Material-Icons/icons/%s";
    private static final String JSON_ARG_COUNT = "count";
    private static final String JSON_ARG_NAME = "name";

    private Context mContext;
    private ProgressDialog mDialog;
    private OnDataLoadedListener mListener;

    private File mIconsDirectory;

    private JSONObject mNetManifest;

    static final class Result {
        String[] files;
        List<String> sectNames;
        List<Integer> sectPos;
    }

    AsyncDataRetriever(Context context, OnDataLoadedListener loadedListener) {
        mContext = context;
        mListener = loadedListener;

        mDialog = new ProgressDialog(context);
                mDialog.setCancelable(false);
		        mDialog.setButton(ProgressDialog.BUTTON_NEGATIVE,Resources.getSystem().getString(android.R.string.cancel),new DialogInterface.OnClickListener(){
				@Override
				public void onClick(DialogInterface p1, int p2) {
                    cancel(false); // Cancel but don't interrupt, we still want already downloaded icons
				}
			});
		
        mIconsDirectory = new File(context.getFilesDir().getAbsolutePath() + MainActivity.ICONS_PATH);
        if (!mIconsDirectory.exists())
            // Create local icons directory
            mIconsDirectory.mkdir();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mDialog.show();
		
    }

    @Override
    protected Result doInBackground(Void... params) {
        publishProgress(mContext.getString(R.string.data_task_msg_1));

        checkFirstLaunch();

        mNetManifest = getNetManifest();

        if (mNetManifest != null) {
            // If network manifest is available, we check if local data is up to date
            checkLocalData();
        } else {
            Utils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, R.string.net_required, Toast.LENGTH_LONG).show();
                }
            });
            Log.e(TAG, "Error when downloading manifest");
        }

        publishProgress(mContext.getString(R.string.data_task_msg_3));

        verifyIconsValidity();

        String[] fileNames = mIconsDirectory.list();
        Arrays.sort(fileNames);

        Utils.Tuple<List<String>, List<Integer>> sectionsInfo = getSectionsInfo(fileNames);

        Result result = new Result();
        result.files = fileNames;
        result.sectNames = sectionsInfo.get0();
        result.sectPos = sectionsInfo.get1();
        return result;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
        mDialog.setMessage(values[0]);
    }

    @Override
    protected void onCancelled(Result result) {
        super.onCancelled(result);
        mDialog.dismiss();

        mListener.onDataLoaded(result.files,
                result.sectNames,
                result.sectPos);
    }

    @Override
    protected void onPostExecute(Result result) {
        super.onPostExecute(result);
        mDialog.dismiss();

        mListener.onDataLoaded(result.files,
                result.sectNames,
                result.sectPos);
    }

    private void checkFirstLaunch() {
        if (mIconsDirectory.list().length == 0) {
            AssetManager manager = mContext.getAssets();
            try {
                for (String assetChild : manager.list("icons")) {

                    String path = mContext.getFilesDir().getAbsolutePath() + MainActivity.ICONS_PATH +
                            File.separator + assetChild;

                    Utils.writeFile(path, manager.open("icons/" + assetChild));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Retrieve, if possible, network manifest.
     */
    private JSONObject getNetManifest() {
        try {
            String rawNetManifest = HttpWrapper.makeRequest(MANIFEST_MAIN_URL);

            if (rawNetManifest != null)
                return new JSONObject(rawNetManifest);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Check if local data is up to date, if not, update it.
     */
    private void checkLocalData() {
        if (isLocalDataUpToDate())
            return;// Data is up to date

        List<JSONObject> itemsToDownload = getItemsToDownload();
        int size = itemsToDownload.size();

        // Download missing items
        for (int i = 0; i < size; i++) {
            if (isCancelled())
                return;

            publishProgress(mContext.getString(R.string.data_task_msg_2, i, size));

            JSONObject json = itemsToDownload.get(i);

            if (!downloadIcon(json)) // Downloading icon
                Log.e(TAG, "Error when downloading following Json: " + json.toString());
        }
    }

    /**
     * Check if local icons count matches server items count.
     *
     * @return True if local data is up to date
     */
    private boolean isLocalDataUpToDate() {
        try {
            return mIconsDirectory.list().length == mNetManifest.getInt(JSON_ARG_COUNT);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Retrieve a list of locally missing icons.
     *
     * @return List of icons to download formatted in JSON
     */
    private List<JSONObject> getItemsToDownload() {
        List<JSONObject> urlsToLoad = new ArrayList<>();

        try {
            // We need the server files.manifest to find missing icons
            String rawNetFilesManifest = HttpWrapper.makeRequest(MANIFEST_FILES_URL);
            JSONArray netItems = new JSONArray(rawNetFilesManifest);

            if (mIconsDirectory.list().length == 0) {
                // Download everything
                for (int i = 0; i < netItems.length(); i++)
                    urlsToLoad.add(netItems.getJSONObject(i));

            } else {
                List<String> localItems = Arrays.asList(mIconsDirectory.list());

                for (int i = 0; i < netItems.length(); i++) {
                    // We iterate through network items to check if each one is present in local folder.

                    JSONObject netJson = netItems.getJSONObject(i);
                    String netItemName = netJson.getString(JSON_ARG_NAME);

                    if (!localItems.contains(netItemName))
                        urlsToLoad.add(netJson);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return urlsToLoad;
    }

    /**
     * Downloads requested item from the server.
     *
     * @param json JSON that contains icon name
     * @return True if download was successful
     */
    private boolean downloadIcon(JSONObject json) {
        Log.i(TAG, "Downloading Json: " + json.toString());

        try {
            // Getting download url
            String url = String.format(FILES_URL_UNFORMATTED, json.getString(JSON_ARG_NAME));

            File outputFile = new File(mContext.getFilesDir().getAbsolutePath() + MainActivity.ICONS_PATH +
                    File.separator + json.getString(JSON_ARG_NAME));

            if (!outputFile.exists())
                outputFile.createNewFile();

            OutputStream outputStream = new FileOutputStream(outputFile);

            // HttpWrapper takes care of closing streams
            HttpWrapper.downloadFile(url, outputStream);

            return true;
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Checks if all icons are valid to avoid svg parse issues later
     */
    private void verifyIconsValidity() {
        for (File child : mIconsDirectory.listFiles()) {
            if (child.length() == 0) {
                child.delete();
                Log.e(TAG, "Error downloading " + child.getName() + ", deleting");
            }
        }
    }

    /**
     * Creates sections info for recycler view. It contains 1000+ items, so do it asynchronously
     * is advised
     *
     * @param filesName final file names
     * @return Tuple containing sections info
     */
    private Utils.Tuple<List<String>, List<Integer>> getSectionsInfo(String[] filesName) {
        List<Integer> sectPositions = new ArrayList<>();
        List<String> sectNames = new ArrayList<>();

        boolean nonLetterAdded = false;
        char prevCh = ' ';

        for (int i = 0; i < filesName.length; i++) {
            String label = filesName[i].toUpperCase();
            Character ch = label.charAt(0);

            //building section with only one time 1st letter
            if (!Character.isLetter(ch)) {
                if (!nonLetterAdded) {
                    sectNames.add("#");
                    sectPositions.add(i);
                    nonLetterAdded = true;
                }
                continue;
            }
            if (!ch.equals(prevCh)) {
                prevCh = ch;
                sectNames.add(ch.toString());
                sectPositions.add(i);
            }
        }

        return new Utils.Tuple<>(sectNames, sectPositions);
    }

    interface OnDataLoadedListener {
        public void onDataLoaded(String[] fileNames, List<String> sectionNames, List<Integer> sectionPositions);
    }
}
