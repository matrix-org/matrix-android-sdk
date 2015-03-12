package org.matrix.matrixandroidsdk.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.matrixandroidsdk.ConsoleApplication;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.activity.CommonActivityUtils;
import org.matrix.matrixandroidsdk.activity.MemberDetailsActivity;
import org.matrix.matrixandroidsdk.adapters.AdapterUtils;
import org.matrix.matrixandroidsdk.adapters.ContactsListAdapter;
import org.matrix.matrixandroidsdk.adapters.MembersInvitationAdapter;
import org.matrix.matrixandroidsdk.contacts.Contact;
import org.matrix.matrixandroidsdk.contacts.PIDsRetriever;
import org.matrix.matrixandroidsdk.services.EventStreamService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * A dialog fragment showing the contacts list
 */
public class ContactsListDialogFragment extends DialogFragment implements PIDsRetriever.PIDsRetrieverListener  {
    private static final String LOG_TAG = "ContactsListDialogFragment";

    private ListView mListView;
    private ContactsListAdapter mAdapter;
    private MXSession mSession;

    public static ContactsListDialogFragment newInstance() {
        ContactsListDialogFragment f = new ContactsListDialogFragment();
        Bundle args = new Bundle();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getActivity().getApplicationContext();

        mSession = Matrix.getInstance(context).getDefaultSession();
        if (mSession == null) {
            throw new RuntimeException("No MXSession.");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle(getString(R.string.contacts));

        View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_contacts_list, null);
        builder.setView(view);
        initView(view);

        return builder.create();
    }

    // Comparator to order members alphabetically
    private Comparator<Contact> alphaComparator = new Comparator<Contact>() {
        @Override
        public int compare(Contact contact1, Contact contact2) {
            String displayname1 = (contact1.mDisplayName == null)? contact1.mContactId : contact1.mDisplayName;
            String displayname2 = (contact2.mDisplayName == null)? contact2.mContactId : contact2.mDisplayName;

            return String.CASE_INSENSITIVE_ORDER.compare(displayname1, displayname2);
        }
    };

    /**
     * List the local contacts.
     * @param context the context.
     * @param cr the content resolver.
     * @return a list of contacts.
     */
    Collection<Contact> getLocalContacts(Context context, ContentResolver cr)
    {
        HashMap<String, Contact> dict = new HashMap<String, Contact>();

        // get the names
        Cursor namesCur = cr.query(ContactsContract.Data.CONTENT_URI,
                new String[]{ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                        ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID,
                        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                },
                ContactsContract.Data.MIMETYPE + " = ?",
                new String[] { ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE }, null);


        if (namesCur != null) {
            while (namesCur.moveToNext()) {
                String displayName = namesCur.getString(namesCur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                String contactId = namesCur.getString(namesCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID));
                String thumbnailUri = namesCur.getString(namesCur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI));

                Contact contact = dict.get(contactId);

                if (null == contact) {
                    contact = new Contact();
                    dict.put(contactId, contact);
                }

                if (null != displayName) {
                    contact.mDisplayName = displayName;
                }

                if (null != thumbnailUri) {
                    contact.mThumbnailUri = thumbnailUri;
                }

                if (null != contactId) {
                    contact.mContactId = contactId;
                }
            }
            namesCur.close();
        }

        // get the phonenumbers
        Cursor phonesCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DATA, // actual number
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                },
                null,null, null);

        if (null != phonesCur) {
            while (phonesCur.moveToNext()) {
                String phone = phonesCur.getString(phonesCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DATA));

                if (!TextUtils.isEmpty(phone)) {
                    String contactId = phonesCur.getString(phonesCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID));

                    Contact contact = dict.get(contactId);
                    if (null == contact) {
                        contact = new Contact();
                        dict.put(contactId, contact);
                    }

                    contact.mPhoneNumbers.add(phone);
                }
            }
            phonesCur.close();
        }

        // get the emails
        Cursor emailsCur = cr.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Email.DATA, // actual email
                        ContactsContract.CommonDataKinds.Email.CONTACT_ID},
                null, null, null);


        if (emailsCur != null) {
            while (emailsCur.moveToNext()) {
                String email = emailsCur.getString(emailsCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));
                if (!TextUtils.isEmpty(email)) {
                    String contactId = emailsCur.getString(emailsCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID));

                    Contact contact = dict.get(contactId);
                    if (null == contact) {
                        contact = new Contact();
                        dict.put(contactId, contact);
                    }

                    contact.mEmails.add(email);
                }
            }
            emailsCur.close();
        }

        return dict.values();
    }

    /**
     * Init the dialog view.
     * @param v the dialog view.
     */
    void initView(View v) {
        mListView = ((ListView)v.findViewById(R.id.listView_contacts));

        // get the local contacts
        ArrayList<Contact> contacts = new ArrayList<Contact>(getLocalContacts(getActivity(), getActivity().getContentResolver()));

        mAdapter = new ContactsListAdapter(getActivity(), R.layout.adapter_item_contact);

        // sort them
        Collections.sort(contacts, alphaComparator);

        // display them
        for(Contact contact : contacts) {
            mAdapter.add(contact);
        }

        mListView.setAdapter(mAdapter);

        // tap on one of them
        // if he is a matrix, offer to start a chat
        // it he is not a matrix user, offer to invite him by email or SMS.
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Contact contact = mAdapter.getItem(position);
                final Activity activity = ContactsListDialogFragment.this.getActivity();

                if (contact.hasMatridIds(ContactsListDialogFragment.this.getActivity())) {
                    final String matrixID = contact.getFirstMatrixId();
                    // The user is trying to leave with unsaved changes. Warn about that
                    new AlertDialog.Builder(activity)
                            .setMessage(activity.getText(R.string.chat_with) + " " + matrixID + " ?")
                            .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            CommonActivityUtils.goToOneToOneRoom(matrixID, activity, new SimpleApiCallback<Void>() {
                                                @Override
                                                public void onMatrixError(MatrixError e) {
                                                }

                                                @Override
                                                public void onNetworkError(Exception e) {
                                                }

                                                @Override
                                                public void onUnexpectedError(Exception e) {
                                                }
                                            });
                                        }
                                    });
                                    dialog.dismiss();
                                    // dismiss the member list
                                    ContactsListDialogFragment.this.dismiss();

                                }
                            })
                            .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .create()
                            .show();

                } else {
                    // invite the user
                    final ArrayList<String> choicesList = new ArrayList<String>();

                    if (AdapterUtils.canSendSms(activity)) {
                        choicesList.addAll(contact.mPhoneNumbers);
                    }

                    choicesList.addAll(contact.mEmails);

                    // something to offer
                    if (choicesList.size() > 0) {
                        final String[] labels = new String[choicesList.size()];

                        for(int index = 0; index < choicesList.size(); index++) {
                            labels[index] = choicesList.get(index);
                        }

                        new AlertDialog.Builder(activity)
                                .setItems(labels, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        String value = labels[which];

                                        // SMS ?
                                        if (contact.mPhoneNumbers.indexOf(value) >= 0) {
                                            AdapterUtils.launchSmsIntent(activity, value, activity.getString(R.string.invitation_message));
                                        } else {
                                            // emails
                                            AdapterUtils.launchEmailIntent(activity, value, activity.getString(R.string.invitation_message));
                                        }

                                        // dismiss the member list
                                        ContactsListDialogFragment.this.dismiss();
                                    }
                                }).setTitle(activity.getString(R.string.invite_this_user_to_use_matrix)).show();
                    }
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        PIDsRetriever.setPIDsRetrieverListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        PIDsRetriever.setPIDsRetrieverListener(null);
    }

    /**
     * Called when the contact PIDs are retrieved
     */
    @Override public void onPIDsRetrieved(Contact contact, final boolean has3PIDs) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // refresh only if there are some updates
                if (has3PIDs) {
                    // other contacts could be retrieved
                    mAdapter.notifyDataSetChanged();
                }
            }
        });
    }
}
