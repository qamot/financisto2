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
package ru.orangesoftware.financisto2.report;

import android.content.Context;
import ru.orangesoftware.financisto2.activity.BlotterActivity;
import ru.orangesoftware.financisto2.activity.SplitsBlotterActivity;
import ru.orangesoftware.financisto2.blotter.BlotterFilter;
import ru.orangesoftware.financisto2.db.CategoryRepository;
import ru.orangesoftware.financisto2.db.CategoryRepository_;
import ru.orangesoftware.financisto2.filter.WhereFilter;
import ru.orangesoftware.financisto2.filter.Criteria;
import ru.orangesoftware.financisto2.db.DatabaseAdapter;
import ru.orangesoftware.financisto2.model.Category;
import ru.orangesoftware.financisto2.model.Currency;

import static ru.orangesoftware.financisto2.db.DatabaseHelper.V_REPORT_CATEGORY;

public class CategoryReportAll extends Report {

	public CategoryReportAll(Context context, Currency currency) {
		super(ReportType.BY_CATEGORY, context, currency);
	}

	@Override
	public ReportData getReport(DatabaseAdapter db, WhereFilter filter) {
        cleanupFilter(filter);
		return queryReport(db, V_REPORT_CATEGORY, filter);
	}
	
	@Override
	public Criteria getCriteriaForId(CategoryRepository categoryRepository, long id) {
		Category c = categoryRepository.getCategoryById(id);
		return Criteria.btw(BlotterFilter.CATEGORY_LEFT, String.valueOf(c.left), String.valueOf(c.right));
	}

    @Override
    public boolean shouldDisplayTotal() {
        return false;
    }

    @Override
    protected Class<? extends BlotterActivity> getBlotterActivityClass() {
        return SplitsBlotterActivity.class;
    }

}
