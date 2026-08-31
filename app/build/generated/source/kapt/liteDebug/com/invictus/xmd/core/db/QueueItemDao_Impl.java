package com.invictus.xmd.core.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.invictus.xmd.core.DownloadCategory;
import com.invictus.xmd.core.ItemStatus;
import com.invictus.xmd.core.MediaPlatform;
import com.invictus.xmd.core.QueueItem;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
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
public final class QueueItemDao_Impl implements QueueItemDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<QueueItem> __insertionAdapterOfQueueItem;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<QueueItem> __deletionAdapterOfQueueItem;

  public QueueItemDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfQueueItem = new EntityInsertionAdapter<QueueItem>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `queue_items` (`id`,`sourceUrl`,`directUrl`,`status`,`fileName`,`filePath`,`error`,`bytesDone`,`bytesTotal`,`speedBps`,`downloadStartedAtMs`,`category`,`customSaveDirPath`,`platform`,`mediaFormatSelector`,`mediaFormatLabel`,`progressPercent`,`mediaStatusText`,`selectedFileIndices`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final QueueItem entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
        if (entity.getSourceUrl() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getSourceUrl());
        }
        if (entity.getDirectUrl() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getDirectUrl());
        }
        final String _tmp = __converters.fromItemStatus(entity.getStatus());
        if (_tmp == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, _tmp);
        }
        if (entity.getFileName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getFileName());
        }
        if (entity.getFilePath() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getFilePath());
        }
        if (entity.getError() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getError());
        }
        statement.bindLong(8, entity.getBytesDone());
        statement.bindLong(9, entity.getBytesTotal());
        statement.bindDouble(10, entity.getSpeedBps());
        statement.bindLong(11, entity.getDownloadStartedAtMs());
        final String _tmp_1 = __converters.fromDownloadCategory(entity.getCategory());
        if (_tmp_1 == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, _tmp_1);
        }
        if (entity.getCustomSaveDirPath() == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, entity.getCustomSaveDirPath());
        }
        final String _tmp_2 = __converters.fromMediaPlatform(entity.getPlatform());
        if (_tmp_2 == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, _tmp_2);
        }
        if (entity.getMediaFormatSelector() == null) {
          statement.bindNull(15);
        } else {
          statement.bindString(15, entity.getMediaFormatSelector());
        }
        if (entity.getMediaFormatLabel() == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.getMediaFormatLabel());
        }
        statement.bindLong(17, entity.getProgressPercent());
        if (entity.getMediaStatusText() == null) {
          statement.bindNull(18);
        } else {
          statement.bindString(18, entity.getMediaStatusText());
        }
        if (entity.getSelectedFileIndices() == null) {
          statement.bindNull(19);
        } else {
          statement.bindString(19, entity.getSelectedFileIndices());
        }
      }
    };
    this.__deletionAdapterOfQueueItem = new EntityDeletionOrUpdateAdapter<QueueItem>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `queue_items` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final QueueItem entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
      }
    };
  }

  @Override
  public Object upsert(final QueueItem item, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfQueueItem.insert(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertAll(final List<QueueItem> items,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfQueueItem.insert(items);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final QueueItem item, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfQueueItem.handle(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAll(final Continuation<? super List<QueueItem>> $completion) {
    final String _sql = "SELECT * FROM queue_items";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<QueueItem>>() {
      @Override
      @NonNull
      public List<QueueItem> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSourceUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceUrl");
          final int _cursorIndexOfDirectUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "directUrl");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfError = CursorUtil.getColumnIndexOrThrow(_cursor, "error");
          final int _cursorIndexOfBytesDone = CursorUtil.getColumnIndexOrThrow(_cursor, "bytesDone");
          final int _cursorIndexOfBytesTotal = CursorUtil.getColumnIndexOrThrow(_cursor, "bytesTotal");
          final int _cursorIndexOfSpeedBps = CursorUtil.getColumnIndexOrThrow(_cursor, "speedBps");
          final int _cursorIndexOfDownloadStartedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "downloadStartedAtMs");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfCustomSaveDirPath = CursorUtil.getColumnIndexOrThrow(_cursor, "customSaveDirPath");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfMediaFormatSelector = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaFormatSelector");
          final int _cursorIndexOfMediaFormatLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaFormatLabel");
          final int _cursorIndexOfProgressPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "progressPercent");
          final int _cursorIndexOfMediaStatusText = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaStatusText");
          final int _cursorIndexOfSelectedFileIndices = CursorUtil.getColumnIndexOrThrow(_cursor, "selectedFileIndices");
          final List<QueueItem> _result = new ArrayList<QueueItem>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QueueItem _item;
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpSourceUrl;
            if (_cursor.isNull(_cursorIndexOfSourceUrl)) {
              _tmpSourceUrl = null;
            } else {
              _tmpSourceUrl = _cursor.getString(_cursorIndexOfSourceUrl);
            }
            final String _tmpDirectUrl;
            if (_cursor.isNull(_cursorIndexOfDirectUrl)) {
              _tmpDirectUrl = null;
            } else {
              _tmpDirectUrl = _cursor.getString(_cursorIndexOfDirectUrl);
            }
            final ItemStatus _tmpStatus;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfStatus)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfStatus);
            }
            _tmpStatus = __converters.toItemStatus(_tmp);
            final String _tmpFileName;
            if (_cursor.isNull(_cursorIndexOfFileName)) {
              _tmpFileName = null;
            } else {
              _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            }
            final String _tmpFilePath;
            if (_cursor.isNull(_cursorIndexOfFilePath)) {
              _tmpFilePath = null;
            } else {
              _tmpFilePath = _cursor.getString(_cursorIndexOfFilePath);
            }
            final String _tmpError;
            if (_cursor.isNull(_cursorIndexOfError)) {
              _tmpError = null;
            } else {
              _tmpError = _cursor.getString(_cursorIndexOfError);
            }
            final long _tmpBytesDone;
            _tmpBytesDone = _cursor.getLong(_cursorIndexOfBytesDone);
            final long _tmpBytesTotal;
            _tmpBytesTotal = _cursor.getLong(_cursorIndexOfBytesTotal);
            final double _tmpSpeedBps;
            _tmpSpeedBps = _cursor.getDouble(_cursorIndexOfSpeedBps);
            final long _tmpDownloadStartedAtMs;
            _tmpDownloadStartedAtMs = _cursor.getLong(_cursorIndexOfDownloadStartedAtMs);
            final DownloadCategory _tmpCategory;
            final String _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCategory)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getString(_cursorIndexOfCategory);
            }
            _tmpCategory = __converters.toDownloadCategory(_tmp_1);
            final String _tmpCustomSaveDirPath;
            if (_cursor.isNull(_cursorIndexOfCustomSaveDirPath)) {
              _tmpCustomSaveDirPath = null;
            } else {
              _tmpCustomSaveDirPath = _cursor.getString(_cursorIndexOfCustomSaveDirPath);
            }
            final MediaPlatform _tmpPlatform;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfPlatform)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfPlatform);
            }
            _tmpPlatform = __converters.toMediaPlatform(_tmp_2);
            final String _tmpMediaFormatSelector;
            if (_cursor.isNull(_cursorIndexOfMediaFormatSelector)) {
              _tmpMediaFormatSelector = null;
            } else {
              _tmpMediaFormatSelector = _cursor.getString(_cursorIndexOfMediaFormatSelector);
            }
            final String _tmpMediaFormatLabel;
            if (_cursor.isNull(_cursorIndexOfMediaFormatLabel)) {
              _tmpMediaFormatLabel = null;
            } else {
              _tmpMediaFormatLabel = _cursor.getString(_cursorIndexOfMediaFormatLabel);
            }
            final int _tmpProgressPercent;
            _tmpProgressPercent = _cursor.getInt(_cursorIndexOfProgressPercent);
            final String _tmpMediaStatusText;
            if (_cursor.isNull(_cursorIndexOfMediaStatusText)) {
              _tmpMediaStatusText = null;
            } else {
              _tmpMediaStatusText = _cursor.getString(_cursorIndexOfMediaStatusText);
            }
            final String _tmpSelectedFileIndices;
            if (_cursor.isNull(_cursorIndexOfSelectedFileIndices)) {
              _tmpSelectedFileIndices = null;
            } else {
              _tmpSelectedFileIndices = _cursor.getString(_cursorIndexOfSelectedFileIndices);
            }
            _item = new QueueItem(_tmpId,_tmpSourceUrl,_tmpDirectUrl,_tmpStatus,_tmpFileName,_tmpFilePath,_tmpError,_tmpBytesDone,_tmpBytesTotal,_tmpSpeedBps,_tmpDownloadStartedAtMs,_tmpCategory,_tmpCustomSaveDirPath,_tmpPlatform,_tmpMediaFormatSelector,_tmpMediaFormatLabel,_tmpProgressPercent,_tmpMediaStatusText,_tmpSelectedFileIndices);
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

  @Override
  public Object deleteByIds(final List<String> ids, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM queue_items WHERE id IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : ids) {
          if (_item == null) {
            _stmt.bindNull(_argIndex);
          } else {
            _stmt.bindString(_argIndex, _item);
          }
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
