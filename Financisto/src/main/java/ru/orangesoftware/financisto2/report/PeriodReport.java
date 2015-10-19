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
import android.database.Cursor;

import ru.orangesoftware.financisto2.db.CategoryRepository;
import ru.orangesoftware.financisto2.filter.WhereFilter;
import ru.orangesoftware.financisto2.datetime.Period;
import ru.orangesoftware.financisto2.datetime.PeriodType;
import ru.orangesoftware.financisto2.filter.Criteria;
import ru.orangesoftware.financisto2.filter.DateTimeCriteria;
import ru.orangesoftware.financisto2.db.DatabaseAdapter;
import ru.orangesoftware.financisto2.db.DatabaseHelper.ReportColumns;
import ru.orangesoftware.financisto2.graph.GraphUnit;
import ru.orangesoftware.financisto2.model.Currency;
import ru.orangesoftware.financisto2.model.Total;

import java.util.ArrayList;

import static ru.orangesoftware.financisto2.db.DatabaseHelper.V_REPORT_PERIOD;
import static ru.orangesoftware.financisto2.datetime.PeriodType.*;

public class PeriodReport extends Report {

    private final PeriodType[] periodTypes = new PeriodType[]{TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_AND_LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_AND_LAST_MONTH};
	private final Period[] periods = new Period[periodTypes.length];

    private Period currentPeriod;

	public PeriodReport(Context context, Currency currency) {
		super(ReportType.BY_PERIOD, context, currency);
        for (int i=0; i<periodTypes.length; i++) {
            periods[i] = periodTypes[i].calculatePeriod();
        }
    }

	@Override
	public ReportData getReport(DatabaseAdapter db, WhereFilter filter) {
		WhereFilter newFilter = WhereFilter.empty();
		Criteria criteria = filter.get(ReportColumns.FROM_ACCOUNT_CURRENCY_ID);
		if (criteria != null) {
			newFilter.put(criteria);
		}
		filterTransfers(newFilter);
		ArrayList<GraphUnit> units = new ArrayList<GraphUnit>();
        for (Period p : periods) {
            currentPeriod = p;
            newFilter.put(Criteria.btw(ReportColumns.DATETIME, String.valueOf(p.start), String.valueOf(p.end)));
            Cursor c = db.db().query(V_REPORT_PERIOD, ReportColumns.NORMAL_PROJECTION,
                    newFilter.getSelection(), newFilter.getSelectionArgs(), null, null, null);
            ArrayList<GraphUnit> u = getUnitsFromCursor(db, c);
            if (u.size() > 0 && u.get(0).size() > 0) {
                units.add(u.get(0));
            }
        }
        Total total = calculateTotal(units);
		return new ReportData(units, total);
	}

    @Override
    protected long getId(Cursor c) {
        return currentPeriod.type.ordinal();
    }

    @Override
    protected String alterName(long id, String name) {
        return context.getString(currentPeriod.type.titleId);
    }

    @Override
	public Criteria getCriteriaForId(CategoryRepository categoryRepository, long id) {
        for (Period period : periods) {
            if (period.type.ordinal() == id) {
                return new DateTimeCriteria(period);
            }
        }
		return null;
	}

    @Override
    public boolean shouldDisplayTotal() {
        return false;
    }

}
