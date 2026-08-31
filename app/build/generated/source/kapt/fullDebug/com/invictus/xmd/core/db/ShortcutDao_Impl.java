package com.invictus.xmd.core.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.invictus.xmd.core.Shortcut;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ShortcutDao_Impl implements ShortcutDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Shortcut> __insertionAdapterOfShortcut;

  private final EntityDeletionOrUpdateAdapter<Shortcut> __deletionAdapterOfShortcut;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public ShortcutDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfShortcut = new EntityInsertionAdapter<Shortcut>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `shortcuts` (`id`,`title`,`url`,`faviconUrl`,`sortOrder`,`createdAtMs`,`customIconPath`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Shortcut entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
        if (entity.getTitle() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getTitle());
        }
        if (entity.getUrl() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getUrl());
        }
        if (entity.getFaviconUrl() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getFaviconUrl());
        }
        statement.bindLong(5, entity.getSortOrder());
        statement.bindLong(6, entity.getCreatedAtMs());
        if (entity.getCustomIconPath() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getCustomIconPath());
        }
      }
    };
    this.__deletionAdapterOfShortcut = new EntityDeletionOrUpdateAdapter<Shortcut>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `shortcuts` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Shortcut entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM shortcuts WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final Shortcut shortcut, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfShortcut.insert(shortcut);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final Shortcut shortcut, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfShortcut.handle(shortcut);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        if (id == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, id);
        }
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public LiveData<List<Shortcut>> observeAll() {
    final String _sql = "SELECT * FROM shortcuts ORDER BY sortOrder ASC, createdAtMs ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"shortcuts"}, false, new Callable<List<Shortcut>>() {
      @Override
      @Nullable
      public List<Shortcut> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfFaviconUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "faviconUrl");
          final int _cursorIndexOfSortOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "sortOrder");
          final int _cursorIndexOfCreatedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtMs");
          final int _cursorIndexOfCustomIconPath = CursorUtil.getColumnIndexOrThrow(_cursor, "customIconPath");
          final List<Shortcut> _result = new ArrayList<Shortcut>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Shortcut _item;
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpUrl;
            if (_cursor.isNull(_cursorIndexOfUrl)) {
              _tmpUrl = null;
            } else {
              _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            }
            final String _tmpFaviconUrl;
            if (_cursor.isNull(_cursorIndexOfFaviconUrl)) {
              _tmpFaviconUrl = null;
            } else {
              _tmpFaviconUrl = _cursor.getString(_cursorIndexOfFaviconUrl);
            }
            final int _tmpSortOrder;
            _tmpSortOrder = _cursor.getInt(_cursorIndexOfSortOrder);
            final long _tmpCreatedAtMs;
            _tmpCreatedAtMs = _cursor.getLong(_cursorIndexOfCreatedAtMs);
            final String _tmpCustomIconPath;
            if (_cursor.isNull(_cursorIndexOfCustomIconPath)) {
              _tmpCustomIconPath = null;
            } else {
              _tmpCustomIconPath = _cursor.getString(_cursorIndexOfCustomIconPath);
            }
            _item = new Shortcut(_tmpId,_tmpTitle,_tmpUrl,_tmpFaviconUrl,_tmpSortOrder,_tmpCreatedAtMs,_tmpCustomIconPath);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAll(final Continuation<? super List<Shortcut>> $completion) {
    final String _sql = "SELECT * FROM shortcuts ORDER BY sortOrder ASC, createdAtMs ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Shortcut>>() {
      @Override
      @NonNull
      public List<Shortcut> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "url");
          final int _cursorIndexOfFaviconUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "faviconUrl");
          final int _cursorIndexOfSortOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "sortOrder");
          final int _cursorIndexOfCreatedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtMs");
          final int _cursorIndexOfCustomIconPath = CursorUtil.getColumnIndexOrThrow(_cursor, "customIconPath");
          final List<Shortcut> _result = new ArrayList<Shortcut>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Shortcut _item;
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpUrl;
            if (_cursor.isNull(_cursorIndexOfUrl)) {
              _tmpUrl = null;
            } else {
              _tmpUrl = _cursor.getString(_cursorIndexOfUrl);
            }
            final String _tmpFaviconUrl;
            if (_cursor.isNull(_cursorIndexOfFaviconUrl)) {
              _tmpFaviconUrl = null;
            } else {
              _tmpFaviconUrl = _cursor.getString(_cursorIndexOfFaviconUrl);
            }
            final int _tmpSortOrder;
            _tmpSortOrder = _cursor.getInt(_cursorIndexOfSortOrder);
            final long _tmpCreatedAtMs;
            _tmpCreatedAtMs = _cursor.getLong(_cursorIndexOfCreatedAtMs);
            final String _tmpCustomIconPath;
            if (_cursor.isNull(_cursorIndexOfCustomIconPath)) {
              _tmpCustomIconPath = null;
            } else {
              _tmpCustomIconPath = _cursor.getString(_cursorIndexOfCustomIconPath);
            }
            _item = new Shortcut(_tmpId,_tmpTitle,_tmpUrl,_tmpFaviconUrl,_tmpSortOrder,_tmpCreatedAtMs,_tmpCustomIconPath);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
