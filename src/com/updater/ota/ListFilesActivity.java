/*
 * Copyright (C) 2012 OTA Updater
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may only use this file in compliance with the license and provided you are not associated with or are in co-operation anyone by the name 'X Vanderpoel'.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.updater.ota;

import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

public class ListFilesActivity extends ListActivity implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    private ArrayAdapter<String> fileListAdapter;
    private ArrayList<String> fileList = new ArrayList<String>();
    private ArrayList<String> pathList = new ArrayList<String>();

    public static final int DL_PATH_LEN = Config.DL_PATH.length();

    private void listFiles(File dir) {
        File[] files = dir.listFiles();
        fileList.clear();
        pathList.clear();
        for (File file : files) {
            fileList.add(file.getPath().substring(DL_PATH_LEN));
            pathList.add(file.getPath());
        }
        fileListAdapter.notifyDataSetChanged();
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fileListAdapter = new ArrayAdapter<String>(this, R.layout.row, R.id.filename, fileList);
        setListAdapter(fileListAdapter);
        listFiles(Config.DL_PATH_FILE);

        this.getListView().setOnItemClickListener(this);
        this.getListView().setOnItemLongClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
        processItem(pos);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
        processItem(pos);
		return true;
    }

    private void processItem(final int pos) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.alert_options_title);
        builder.setItems(R.array.file_actions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                String path = pathList.get(pos);
                final File file = new File(path);

                AlertDialog.Builder alert;
                switch (which) {
                case 0:
                	installFileDialog(ListFilesActivity.this, file);
                    break;
                case 1:
                    alert = new AlertDialog.Builder(ListFilesActivity.this);
                    alert.setTitle(R.string.alert_rename_title);
                    alert.setMessage(R.string.alert_rename_message);

                    final EditText input = new EditText(ListFilesActivity.this);
                    alert.setView(input);
                    alert.setPositiveButton(R.string.alert_rename, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            dialog.dismiss();

                            String newName = input.getText().toString();
                            if (!newName.endsWith(".zip")) newName += ".zip";
                            File newFile = new File(Config.DL_PATH_FILE, newName);
                            boolean renamed = file.renameTo(newFile);

                            if (renamed) {
                                Toast.makeText(getApplicationContext(), R.string.toast_rename, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.toast_rename_error, Toast.LENGTH_SHORT).show();
                            }

                            listFiles(Config.DL_PATH_FILE);
                            return;
                        }
                    });
                    alert.setNegativeButton(R.string.alert_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    alert.create().show();
                    break;
                case 2:
                    boolean deleted = file.delete();

                    if (deleted) {
                        Toast.makeText(getApplicationContext(), R.string.toast_delete, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), R.string.toast_delete_error, Toast.LENGTH_SHORT).show();
                    }

                    listFiles(Config.DL_PATH_FILE);
                    break;
                }
            }
        });

        builder.setCancelable(true);
        builder.create().show();
    }

    protected static void installFileDialog(final Context ctx, final File file) {
    	Resources r = ctx.getResources();
        String[] installOpts = r.getStringArray(R.array.install_options);
        final boolean[] selectedOpts = new boolean[installOpts.length];
        selectedOpts[selectedOpts.length - 1] = true;

        AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
        alert.setTitle(R.string.alert_install_title);
//        alert.setMessage(R.string.alert_install_message);
        alert.setMultiChoiceItems(installOpts, selectedOpts, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                selectedOpts[which] = isChecked;
            }
        });
        alert.setPositiveButton(R.string.alert_install, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                
                AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
                alert.setTitle(R.string.alert_install_title);
                alert.setMessage(R.string.alert_install_message);
                alert.setPositiveButton(R.string.alert_install, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            String path = file.getAbsolutePath();
                            if (path.startsWith("/mnt")) path = path.substring(4);
                            
                            Process p = Runtime.getRuntime().exec("su");
                            DataOutputStream os = new DataOutputStream(p.getOutputStream());
                            os.writeBytes("rm -f /cache/recovery/command\n");
                            os.writeBytes("rm -f /cache/recovery/extendedcommand\n");
//                            if (selectedOpts[0]) {
//                                os.writeBytes("echo 'backup_rom /sdcard/clockwordmod/backup/" + 
//                                        new SimpleDateFormat("yyyy-MM-dd_HH.mm").format(new Date()) + 
//                                        "' >> /cache/recovery/extendedcommand\n");
//                            }
                            if (selectedOpts[0]) {
                                os.writeBytes("echo '--wipe_data' >> /cache/recovery/command\n");
                            }
                            if (selectedOpts[1]) {
                                os.writeBytes("echo '--wipe_cache' >> /cache/recovery/command\n");
                            }
                            os.writeBytes("echo '--update_package=" + path + "' >> /cache/recovery/command\n");
                            os.writeBytes("reboot recovery\n");
                            os.writeBytes("exit\n");
                            os.flush();
                            p.waitFor();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                alert.setNegativeButton(R.string.alert_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                alert.create().show();
            }
        });
        alert.setNegativeButton(R.string.alert_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.create().show();
    }
}