/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package ru.orangesoftware.financisto2.activity;

import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.TimePicker;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenuItem;

import java.io.File;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import ru.orangesoftware.financisto2.R;
import ru.orangesoftware.financisto2.datetime.DateUtils;
import ru.orangesoftware.financisto2.db.DatabaseHelper.AccountColumns;
import ru.orangesoftware.financisto2.db.DatabaseHelper.TransactionColumns;
import ru.orangesoftware.financisto2.model.Account;
import ru.orangesoftware.financisto2.model.Attribute;
import ru.orangesoftware.financisto2.model.Category;
import ru.orangesoftware.financisto2.model.Payee;
import ru.orangesoftware.financisto2.model.SystemAttribute;
import ru.orangesoftware.financisto2.model.Transaction;
import ru.orangesoftware.financisto2.model.TransactionAttribute;
import ru.orangesoftware.financisto2.model.TransactionStatus;
import ru.orangesoftware.financisto2.recur.NotificationOptions;
import ru.orangesoftware.financisto2.recur.Recurrence;
import ru.orangesoftware.financisto2.utils.EnumUtils;
import ru.orangesoftware.financisto2.utils.MyPreferences;
import ru.orangesoftware.financisto2.utils.TransactionUtils;
import ru.orangesoftware.financisto2.view.AttributeView;
import ru.orangesoftware.financisto2.view.AttributeViewFactory;
import ru.orangesoftware.financisto2.widget.RateLayoutView;

import static ru.orangesoftware.financisto2.utils.ThumbnailUtil.PICTURES_DIR;
import static ru.orangesoftware.financisto2.utils.ThumbnailUtil.PICTURES_THUMB_DIR;
import static ru.orangesoftware.financisto2.utils.ThumbnailUtil.PICTURE_FILE_NAME_FORMAT;
import static ru.orangesoftware.financisto2.utils.ThumbnailUtil.createAndStoreImageThumbnail;
import static ru.orangesoftware.financisto2.utils.Utils.text;

@EActivity
public abstract class AbstractTransactionActivity extends AbstractActivity implements CategorySelector.CategorySelectorListener {
	
	private static final int RECURRENCE_REQUEST = 4003;
	private static final int NOTIFICATION_REQUEST = 4004;
	private static final int PICTURE_REQUEST = 4005;

	private static final TransactionStatus[] statuses = TransactionStatus.values();

    @Extra
    protected long accountId = -1;
    @Extra
    protected long transactionId = -1;
    @Extra
    protected long transactionDateTime = System.currentTimeMillis();
    @Extra
    protected boolean isDuplicate = false;
    @Extra
    boolean isNewFromTemplate = false;
    @Extra
    int template = 0;

    @OptionsMenuItem(R.id.menu_save_and_new)
    protected MenuItem saveAndNewMenuItem;

    protected RateLayoutView rateView;

    protected EditText templateName;
	protected TextView accountText;	
	protected Cursor accountCursor;
	protected ListAdapter accountAdapter;
	
	protected Calendar dateTime;
	protected ImageButton status;
	protected Button dateText;
	protected Button timeText;
	
    protected EditText noteText;
	protected TextView recurText;	
	protected TextView notificationText;	
	
	private ImageView pictureView;
	private String pictureFileName;
	
	private CheckBox ccardPayment;
	
	protected Account selectedAccount;

	protected String recurrence;
	protected String notificationOptions;
	
    protected ProjectSelector projectSelector;
    protected CategorySelector categorySelector;

    protected boolean isRememberLastAccount;
	protected boolean isRememberLastCategory;
	protected boolean isRememberLastProject;
	protected boolean isShowNote;
	protected boolean isShowTakePicture;
	protected boolean isShowIsCCardPayment;
	protected boolean isOpenCalculatorForTemplates;

    protected boolean isShowPayee = true;
    protected AutoCompleteTextView payeeText;
    protected SimpleCursorAdapter payeeAdapter;

	protected AttributeView deleteAfterExpired;
	
	protected DateFormat df;
	protected DateFormat tf;
	
	protected Transaction transaction = new Transaction();

    public AbstractTransactionActivity() {}
	
	protected abstract int getLayoutId();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        df = DateUtils.getLongDateFormat(this);
        tf = DateUtils.getTimeFormat(this);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(getLayoutId());
    }

    @AfterViews
    public void initViews() {
        long t0 = System.currentTimeMillis();

		isRememberLastAccount = MyPreferences.isRememberAccount(this);
		isRememberLastCategory = isRememberLastAccount && MyPreferences.isRememberCategory(this);
		isRememberLastProject = isRememberLastCategory && MyPreferences.isRememberProject(this);
		isShowNote = MyPreferences.isShowNote(this);
		isShowTakePicture = MyPreferences.isShowTakePicture(this);
		isShowIsCCardPayment = MyPreferences.isShowIsCCardPayment(this);
        isOpenCalculatorForTemplates = MyPreferences.isOpenCalculatorForTemplates(this);

        fetchCategories();

        projectSelector = new ProjectSelector(this, x);
        projectSelector.fetchProjects();

        transaction.dateTime = transactionDateTime;
        if (transactionId != -1) {
            transaction = db.getTransaction(transactionId);
            transaction.categoryAttributes = db.getAllAttributesForTransaction(transactionId);
            if (isDuplicate) {
                transaction.id = -1;
                transaction.dateTime = System.currentTimeMillis();
            }
        }
        if (template > 0) {
            transaction.isTemplate = template;
        }

		if (transaction.id == -1) {
			accountCursor = db.getAllActiveAccounts();
		} else {
			accountCursor = db.getAccountsForTransaction(transaction);
		}
		startManagingCursor(accountCursor);
		accountAdapter = TransactionUtils.createAccountAdapter(this, accountCursor);		
		
		dateTime = Calendar.getInstance();
		Date date = dateTime.getTime();

		status = (ImageButton)findViewById(R.id.status);
		status.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				ArrayAdapter<String> adapter = EnumUtils.createDropDownAdapter(AbstractTransactionActivity.this, statuses);
				x.selectPosition(AbstractTransactionActivity.this, R.id.status, R.string.transaction_status, adapter, transaction.status.ordinal());
			}
		});
		
		dateText = (Button)findViewById(R.id.date);
		dateText.setText(df.format(date));
		dateText.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				DatePickerDialog d = new DatePickerDialog(AbstractTransactionActivity.this, new OnDateSetListener(){
					@Override
					public void onDateSet(DatePicker arg0, int y, int m, int d) {
						dateTime.set(y, m, d);
						dateText.setText(df.format(dateTime.getTime()));
					}					
				}, dateTime.get(Calendar.YEAR), dateTime.get(Calendar.MONTH), dateTime.get(Calendar.DAY_OF_MONTH));
				d.show();
			}			
		});

		timeText = (Button)findViewById(R.id.time);
		timeText.setText(tf.format(date));
		timeText.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View arg0) {
				boolean is24Format = DateUtils.is24HourFormat(AbstractTransactionActivity.this);
				TimePickerDialog d = new TimePickerDialog(AbstractTransactionActivity.this, new OnTimeSetListener(){
					@Override
					public void onTimeSet(TimePicker picker, int h, int m) {
						dateTime.set(Calendar.HOUR_OF_DAY, picker.getCurrentHour());
						dateTime.set(Calendar.MINUTE, picker.getCurrentMinute());
						timeText.setText(tf.format(dateTime.getTime()));
					}
				}, dateTime.get(Calendar.HOUR_OF_DAY), dateTime.get(Calendar.MINUTE), is24Format);
				d.show();
			}			
		});
			
		internalOnCreate();
		
		LinearLayout layout = (LinearLayout)findViewById(R.id.list);
		
		this.templateName = new EditText(this);
		if (transaction.isTemplate()) {
			x.addEditNode(layout, R.string.template_name, templateName);
		}

        rateView = new RateLayoutView(this, x, layout);

        createListNodes(layout);
		categorySelector.createAttributesLayout(layout);
		createCommonNodes(layout);
		
		if (transaction.isScheduled()) {
			recurText = x.addListNode(layout, R.id.recurrence_pattern, R.string.recur, R.string.recur_interval_no_recur);
			notificationText = x.addListNode(layout, R.id.notification, R.string.notification, R.string.notification_options_default);
			Attribute sa = db.getSystemAttribute(SystemAttribute.DELETE_AFTER_EXPIRED);
			deleteAfterExpired = AttributeViewFactory.createViewForAttribute(this, sa);
			String value = transaction.getSystemAttribute(SystemAttribute.DELETE_AFTER_EXPIRED);
			deleteAfterExpired.inflateView(layout, value != null ? value : sa.defaultValue);
		}

		if (transactionId != -1) {
            isOpenCalculatorForTemplates &= isNewFromTemplate;
			editTransaction(transaction);
		} else {
            setDateTime(transaction.dateTime);
            categorySelector.selectCategory(0);
            if (accountId != -1) {
                selectAccount(accountId);
            } else {
                long lastAccountId = MyPreferences.getLastAccount(this);
                if (isRememberLastAccount && lastAccountId != -1) {
                    selectAccount(lastAccountId);
                }
            }
			if (!isRememberLastProject) {
				projectSelector.selectProject(0);
			}
			if (transaction.isScheduled()) {
				selectStatus(TransactionStatus.PN);
			}
		}
		
		long t1 = System.currentTimeMillis();
		Log.i("TransactionActivity", "onCreate "+(t1-t0)+"ms");
	}

    protected void internalOnCreate() {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        if (transaction.id > 0) {
            saveAndNewMenuItem.setTitle(R.string.cancel);
        }
        return result;
    }

    protected void createPayeeNode(LinearLayout layout) {
        payeeAdapter = TransactionUtils.createPayeeAdapter(this, db);
        payeeText = new AutoCompleteTextView(this);
        payeeText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                InputType.TYPE_TEXT_VARIATION_FILTER);
        payeeText.setThreshold(1);
        payeeText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    payeeText.setAdapter(payeeAdapter);
                    payeeText.selectAll();
                }
            }
        });
        x.addEditNode(layout, R.string.payee, payeeText);
    }

    protected abstract void fetchCategories();

    @OptionsItem(R.id.menu_save)
    protected boolean saveAndFinish() {
        long id = save();
        if (id > 0) {
            Intent data = new Intent();
            data.putExtra(TransactionColumns._id.name(), id);
            setResult(RESULT_OK, data);
            finish();
            return true;
        }
        return false;
    }

    @OptionsItem(R.id.menu_save_and_new)
    protected void saveAndNew() {
        if (transaction.id > 0) {
            setResult(RESULT_CANCELED);
            finish();
        } else {
            if (saveAndFinish()) {
                //intent.putExtra(DATETIME_EXTRA, transaction.dateTime);
                //startActivityForResult(intent, -1);
            }
        }
    }

    private long save() {
        if (onOKClicked()) {
            boolean isNew = transaction.id == -1;
            long id = db.insertOrUpdate(transaction, getAttributes());
            if (isNew) {
                MyPreferences.setLastAccount(this, transaction.fromAccountId);
            }
            AccountWidget.updateWidgets(this);
            return id;
        }
        return -1;
    }

    private List<TransactionAttribute> getAttributes() {
        List<TransactionAttribute> attributes = categorySelector.getAttributes();
        if (deleteAfterExpired != null) {
            TransactionAttribute ta = deleteAfterExpired.newTransactionAttribute();
            attributes.add(ta);
        }
        return attributes;
    }


    @Override
    protected boolean shouldLock() {
        return MyPreferences.isPinProtectedNewTransaction(this);
    }

	protected void createCommonNodes(LinearLayout layout) {
		int noteOrder = MyPreferences.getNoteOrder(this);
		int projectOrder = MyPreferences.getProjectOrder(this);
		for (int i=0; i<6; i++) {
			if (i == noteOrder) {
				if (isShowNote) {
					//note
					noteText = new EditText(this);
					x.addEditNode(layout, R.string.note, noteText);
				}
			}
			if (i == projectOrder) {
                projectSelector.createNode(layout);
			}
		}
		if (isShowTakePicture && transaction.isNotTemplateLike()) {
			pictureView = x.addPictureNodeMinus(this, layout, R.id.attach_picture, R.id.delete_picture, R.string.attach_picture, R.string.new_picture);
		}
		if (isShowIsCCardPayment) {
			// checkbox to register if the transaction is a credit card payment. 
			// this will be used to exclude from totals in bill preview
			ccardPayment = x.addCheckboxNode(layout, R.id.is_ccard_payment,
					R.string.is_ccard_payment, R.string.is_ccard_payment_summary, false);
		}
	}

    protected abstract void createListNodes(LinearLayout layout);
	
	protected abstract boolean onOKClicked();

	@Override
	protected void onClick(View v, int id) {
        projectSelector.onClick(id);
        categorySelector.onClick(id);
		switch(id) {
			case R.id.account:				
				x.select(this, R.id.account, R.string.account, accountCursor, accountAdapter,
                        AccountColumns.ID, getSelectedAccountId());
				break;
			case R.id.recurrence_pattern: {
				Intent intent = new Intent(this, RecurrenceActivity.class);
				intent.putExtra(RecurrenceActivity.RECURRENCE_PATTERN, recurrence);
				startActivityForResult(intent, RECURRENCE_REQUEST);				
				break;
			}
			case R.id.notification: {
				Intent intent = new Intent(this, NotificationOptionsActivity.class);
				intent.putExtra(NotificationOptionsActivity.NOTIFICATION_OPTIONS, notificationOptions);
				startActivityForResult(intent, NOTIFICATION_REQUEST);				
				break;
			}
			case R.id.attach_picture: {
				PICTURES_DIR.mkdirs();
				PICTURES_THUMB_DIR.mkdirs();				
				pictureFileName = PICTURE_FILE_NAME_FORMAT.format(new Date())+".jpg";
				transaction.blobKey=null;		
				Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, 
						Uri.fromFile(new File(PICTURES_DIR, pictureFileName)));
				startActivityForResult(intent, PICTURE_REQUEST);
				break;
			}
			case R.id.delete_picture: {
				removePicture();
				break;
			}
			case R.id.is_ccard_payment: {
				ccardPayment.setChecked(!ccardPayment.isChecked());
				transaction.isCCardPayment = ccardPayment.isChecked()?1:0;
			}
		}
	}	

	@Override
	public void onSelectedPos(int id, int selectedPos) {
        projectSelector.onSelectedPos(id, selectedPos);
		switch(id) {
			case R.id.status:
				selectStatus(statuses[selectedPos]);
				break;
		}
	}
 
	@Override
	public void onSelectedId(int id, long selectedId) {
        categorySelector.onSelectedId(id, selectedId);
		switch(id) {
			case R.id.account:				
				selectAccount(selectedId);
				break;
		}
	}
	
	private void selectStatus(TransactionStatus transactionStatus) {
		transaction.status = transactionStatus;
		status.setImageResource(transactionStatus.iconId);
	}

	protected Account selectAccount(long accountId) {
		return selectAccount(accountId, true);
	}
	
	protected Account selectAccount(long accountId, boolean selectLast) {
        Account a = db.getAccount(accountId);
        if (a != null) {
            accountText.setText(a.title);
            rateView.selectCurrencyFrom(a.currency);
            selectedAccount = a;
        }
        return a;
	}

    protected long getSelectedAccountId() {
        return selectedAccount != null ? selectedAccount.id : -1;
    }

    @Override
    public void onCategorySelected(Category category, boolean selectLast) {
        addOrRemoveSplits();
        categorySelector.addAttributes(transaction);
        switchIncomeExpenseButton(category);
        if (selectLast && isRememberLastProject) {
            projectSelector.selectProject(category.lastProjectId);
        }
        projectSelector.setProjectNodeVisible(!category.isSplit());
    }

    protected void addOrRemoveSplits() {
    }

    protected void switchIncomeExpenseButton(Category category) {

    }

	private void setRecurrence(String recurrence) {
		this.recurrence = recurrence;
		if (recurrence == null) {
			recurText.setText(R.string.recur_interval_no_recur);
			dateText.setEnabled(true);
			timeText.setEnabled(true);
		} else {			
			dateText.setEnabled(false);
			timeText.setEnabled(false);
			Recurrence r = Recurrence.parse(recurrence);
			recurText.setText(r.toInfoString(this));
		}
	}

	private void setNotification(String notificationOptions) {
		this.notificationOptions = notificationOptions;
		if (notificationOptions == null) {
			notificationText.setText(R.string.notification_options_default);
		} else {			
			NotificationOptions o = NotificationOptions.parse(notificationOptions);
			notificationText.setText(o.toInfoString(this));
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
        projectSelector.onActivityResult(requestCode, resultCode, data);
        categorySelector.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
            rateView.onActivityResult(requestCode, data);
			switch (requestCode) {
				case RECURRENCE_REQUEST:
					String recurrence = data.getStringExtra(RecurrenceActivity.RECURRENCE_PATTERN);
					setRecurrence(recurrence);
					break;
				case NOTIFICATION_REQUEST:					
					String notificationOptions = data.getStringExtra(NotificationOptionsActivity.NOTIFICATION_OPTIONS);
					setNotification(notificationOptions);
					break;
				case PICTURE_REQUEST:
					selectPicture(pictureFileName);	
					break;
				default:
					break;
			}
		} else {
			if (requestCode == PICTURE_REQUEST) {
				removePicture();
			}
		}
	}
	
	private void selectPicture(String pictureFileName) {
		if (pictureView == null) {
			return;
		}
		if (pictureFileName == null) {
			return;
		}
		File pictureFile = new File(PICTURES_DIR, pictureFileName);
		if (pictureFile.exists()) {
			Bitmap thumb = createThumbnail(pictureFile);					
			pictureView.setImageBitmap(thumb);
			pictureView.setTag(pictureFileName);
			transaction.attachedPicture = pictureFileName;
			
		}				
	}
	
	private void removePicture() {
		if (pictureView == null) {
			return;
		}
		if (pictureFileName != null) {
			new File(PICTURES_DIR, pictureFileName).delete();
			new File(PICTURES_THUMB_DIR, pictureFileName).delete();
		}		
		pictureFileName = null;
		transaction.attachedPicture = null;
		transaction.blobKey=null;	
		pictureView.setImageBitmap(null);		
		pictureView.setTag(null);
	}

	private Bitmap createThumbnail(File pictureFile) {
		return createAndStoreImageThumbnail(getContentResolver(), pictureFile);
	}

	protected void setDateTime(long date) {
		Date d = new Date(date);
		dateTime.setTime(d);
		dateText.setText(df.format(d));
		timeText.setText(tf.format(d));		
	}

    protected abstract void editTransaction(Transaction transaction);

	protected void commonEditTransaction(Transaction transaction) {
		selectStatus(transaction.status);
		categorySelector.selectCategory(transaction.categoryId, false);
		projectSelector.selectProject(transaction.projectId);
		setDateTime(transaction.dateTime);		
		if (isShowNote) {
			noteText.setText(transaction.note);
		}
		if (transaction.isTemplate()) {
			templateName.setText(transaction.templateName);
		}
		if (transaction.isScheduled()) {
			setRecurrence(transaction.recurrence);
			setNotification(transaction.notificationOptions);
		}
		if (isShowTakePicture) {
			selectPicture(transaction.attachedPicture);
		}
		if (isShowIsCCardPayment) {
			setIsCCardPayment(transaction.isCCardPayment);
		}

        if (transaction.isCreatedFromTemlate()&& isOpenCalculatorForTemplates ){
            rateView.openFromAmountCalculator();
        }
	}

	private void setIsCCardPayment(int isCCardPaymentValue) {
		transaction.isCCardPayment = isCCardPaymentValue;
		ccardPayment.setChecked(isCCardPaymentValue==1);
	}

	protected void updateTransactionFromUI(Transaction transaction) {
		transaction.categoryId = categorySelector.getSelectedCategoryId();
		transaction.projectId = projectSelector.getSelectedProjectId();
		if (transaction.isScheduled()) {
			DateUtils.zeroSeconds(dateTime);
		}
		transaction.dateTime = dateTime.getTime().getTime();
        if (isShowPayee) {
            transaction.payeeId = db.insertPayee(text(payeeText)).id;
        }
		if (isShowNote) {
			transaction.note = text(noteText);
		}
		if (transaction.isTemplate()) {
			transaction.templateName = text(templateName);
		}
		if (transaction.isScheduled()) {
			transaction.recurrence = recurrence;
			transaction.notificationOptions = notificationOptions;
		}
	}

    protected void selectPayee(long payeeId) {
        if (isShowPayee) {
            Payee p = db.get(Payee.class, payeeId);
            selectPayee(p);
        }
    }

    protected void selectPayee(Payee p) {
        if (p != null) {
            payeeText.setText(p.title);
            transaction.payeeId = p.id;
        }
    }

}
