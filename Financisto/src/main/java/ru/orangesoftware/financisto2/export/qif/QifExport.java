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
package ru.orangesoftware.financisto2.export.qif;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.support.v4.util.LongSparseArray;

import ru.orangesoftware.financisto2.blotter.BlotterFilter;
import ru.orangesoftware.financisto2.db.CategoryRepository;
import ru.orangesoftware.financisto2.db.CategoryRepository_;
import ru.orangesoftware.financisto2.filter.WhereFilter;
import ru.orangesoftware.financisto2.db.DatabaseAdapter;
import ru.orangesoftware.financisto2.export.Export;
import ru.orangesoftware.financisto2.filter.Criteria;
import ru.orangesoftware.financisto2.model.Account;
import ru.orangesoftware.financisto2.model.Category;
import ru.orangesoftware.financisto2.model.CategoryTree;
import ru.orangesoftware.financisto2.model.Transaction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QifExport extends Export {

    private final DatabaseAdapter db;
    private final CategoryRepository categoryRepository;
    private final QifExportOptions options;
    private final CategoryTree categoryTree;
    private final LongSparseArray<Category> categoriesMap;
    private final LongSparseArray<Account> accountsMap;

    public QifExport(Context context, DatabaseAdapter db, CategoryRepository categoryRepository, QifExportOptions options) {
        super(context, false);
        this.db = db;
        this.categoryRepository =  categoryRepository;
        this.options = options;
        this.categoryTree = categoryRepository.loadCategories();
        this.categoriesMap = categoryTree.asIdMap();
        this.accountsMap = db.getAllAccountsMap();
    }

    @Override
    protected void writeHeader(BufferedWriter bw) throws IOException, PackageManager.NameNotFoundException {
        // no header
    }

    @Override
    protected void writeBody(BufferedWriter bw) throws IOException {
        QifBufferedWriter qifWriter = new QifBufferedWriter(bw);
        writeCategories(qifWriter);
        writeAccountsAndTransactions(qifWriter);
    }

    private void writeCategories(QifBufferedWriter qifWriter) throws IOException {
        if (categoryTree.getRoot().hasChildren()) {
            qifWriter.writeCategoriesHeader();
            for (Category c : categoryTree.getRoot().children) {
                writeCategory(qifWriter, c);
            }
        }
    }

    private void writeCategory(QifBufferedWriter qifWriter, Category c) throws IOException {
        QifCategory qifCategory = QifCategory.fromCategory(c);
        qifCategory.writeTo(qifWriter);
        if (c.hasChildren()) {
            for (Category child : c.children) {
                writeCategory(qifWriter, child);
            }
        }
    }

    private void writeAccountsAndTransactions(QifBufferedWriter qifWriter) throws IOException {
        List<Account> accounts = db.getAllAccountsList();
        for (Account a : accounts) {
            if (isSelectedAccount(a)) {
                QifAccount qifAccount = writeAccount(qifWriter, a);
                writeTransactionsForAccount(qifWriter, qifAccount, a);
            }
        }
    }

    private boolean isSelectedAccount(Account a) {
        long[] selectedAccounts = options.selectedAccounts;
        if (selectedAccounts == null || selectedAccounts.length == 0) {
            return true;
        }
        for (long id : selectedAccounts) {
            if (id == a.id) {
                return true;
            }
        }
        return false;
    }

    private QifAccount writeAccount(QifBufferedWriter qifWriter, Account a) throws IOException {
        QifAccount qifAccount = QifAccount.fromAccount(a);
        qifAccount.writeTo(qifWriter);
        return qifAccount;
    }

    private void writeTransactionsForAccount(QifBufferedWriter qifWriter, QifAccount qifAccount, Account account) throws IOException {
        Cursor c = getBlotterForAccount(account);
        try {
            boolean addHeader = true;
            while (c.moveToNext()) {
                if (addHeader) {
                    qifWriter.write("!Type:").write(qifAccount.type).newLine();
                    addHeader = false;
                }
                QifTransaction qifTransaction = QifTransaction.fromBlotterCursor(c, categoriesMap);
                if (qifTransaction.isSplit()) {
                    List<QifTransaction> qifSplits = fromTransactions(
                            db.getSplitsForTransaction(qifTransaction.id), categoriesMap, accountsMap);
                    qifTransaction.setSplits(qifSplits);
                }
                qifTransaction.writeTo(qifWriter, options);
            }
        } finally {
            c.close();
        }
    }

    private List<QifTransaction> fromTransactions(List<Transaction> transactions, LongSparseArray<Category> categoriesMap, LongSparseArray<Account> accountsMap) {
        List<QifTransaction> qifTransactions = new ArrayList<QifTransaction>(transactions.size());
        for (Transaction transaction : transactions) {
            QifTransaction qifTransaction = QifTransaction.fromTransaction(transaction, categoriesMap, accountsMap);
            qifTransactions.add(qifTransaction);
        }
        return qifTransactions;
    }

    private Cursor getBlotterForAccount(Account account) {
        WhereFilter accountFilter = WhereFilter.copyOf(options.filter);
        accountFilter.put(Criteria.eq(BlotterFilter.FROM_ACCOUNT_ID, String.valueOf(account.id)));
        return db.getBlotterForAccount(accountFilter);
    }

    @Override
    protected void writeFooter(BufferedWriter bw) throws IOException {
        // no footer
    }

    @Override
    protected String getExtension() {
        return ".qif";
    }

}
