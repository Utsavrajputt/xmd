package com.invictus.xmd.core.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile QueueItemDao _queueItemDao;

  private volatile ShortcutDao _shortcutDao;

  private volatile HistoryDao _historyDao;

  private volatile BookmarkDao _bookmarkDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(11) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `queue_items` (`id` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `directUrl` TEXT, `status` TEXT NOT NULL, `fileName` TEXT, `filePath` TEXT, `error` TEXT, `bytesDone` INTEGER NOT NULL, `bytesTotal` INTEGER NOT NULL, `speedBps` REAL NOT NULL, `downloadStartedAtMs` INTEGER NOT NULL, `category` TEXT NOT NULL, `customSaveDirPath` TEXT, `platform` TEXT NOT NULL, `mediaFormatSelector` TEXT, `mediaFormatLabel` TEXT, `progressPercent` INTEGER NOT NULL, `mediaStatusText` TEXT, `selectedFileIndices` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `shortcuts` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `faviconUrl` TEXT, `sortOrder` INTEGER NOT NULL, `createdAtMs` INTEGER NOT NULL, `customIconPath` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `history_entries` (`id` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `visitedAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `faviconUrl` TEXT, `createdAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'dd99df4f85314db83549714e2d16058e')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `queue_items`");
        db.execSQL("DROP TABLE IF EXISTS `shortcuts`");
        db.execSQL("DROP TABLE IF EXISTS `history_entries`");
        db.execSQL("DROP TABLE IF EXISTS `bookmarks`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsQueueItems = new HashMap<String, TableInfo.Column>(19);
        _columnsQueueItems.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("sourceUrl", new TableInfo.Column("sourceUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("directUrl", new TableInfo.Column("directUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("fileName", new TableInfo.Column("fileName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("filePath", new TableInfo.Column("filePath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("error", new TableInfo.Column("error", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("bytesDone", new TableInfo.Column("bytesDone", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("bytesTotal", new TableInfo.Column("bytesTotal", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("speedBps", new TableInfo.Column("speedBps", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("downloadStartedAtMs", new TableInfo.Column("downloadStartedAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("customSaveDirPath", new TableInfo.Column("customSaveDirPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("platform", new TableInfo.Column("platform", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("mediaFormatSelector", new TableInfo.Column("mediaFormatSelector", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("mediaFormatLabel", new TableInfo.Column("mediaFormatLabel", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("progressPercent", new TableInfo.Column("progressPercent", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("mediaStatusText", new TableInfo.Column("mediaStatusText", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueueItems.put("selectedFileIndices", new TableInfo.Column("selectedFileIndices", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysQueueItems = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesQueueItems = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoQueueItems = new TableInfo("queue_items", _columnsQueueItems, _foreignKeysQueueItems, _indicesQueueItems);
        final TableInfo _existingQueueItems = TableInfo.read(db, "queue_items");
        if (!_infoQueueItems.equals(_existingQueueItems)) {
          return new RoomOpenHelper.ValidationResult(false, "queue_items(com.invictus.xmd.core.QueueItem).\n"
                  + " Expected:\n" + _infoQueueItems + "\n"
                  + " Found:\n" + _existingQueueItems);
        }
        final HashMap<String, TableInfo.Column> _columnsShortcuts = new HashMap<String, TableInfo.Column>(7);
        _columnsShortcuts.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShortcuts.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShortcuts.put("url", new TableInfo.Column("url", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShortcuts.put("faviconUrl", new TableInfo.Column("faviconUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShortcuts.put("sortOrder", new TableInfo.Column("sortOrder", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShortcuts.put("createdAtMs", new TableInfo.Column("createdAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsShortcuts.put("customIconPath", new TableInfo.Column("customIconPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysShortcuts = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesShortcuts = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoShortcuts = new TableInfo("shortcuts", _columnsShortcuts, _foreignKeysShortcuts, _indicesShortcuts);
        final TableInfo _existingShortcuts = TableInfo.read(db, "shortcuts");
        if (!_infoShortcuts.equals(_existingShortcuts)) {
          return new RoomOpenHelper.ValidationResult(false, "shortcuts(com.invictus.xmd.core.Shortcut).\n"
                  + " Expected:\n" + _infoShortcuts + "\n"
                  + " Found:\n" + _existingShortcuts);
        }
        final HashMap<String, TableInfo.Column> _columnsHistoryEntries = new HashMap<String, TableInfo.Column>(4);
        _columnsHistoryEntries.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHistoryEntries.put("url", new TableInfo.Column("url", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHistoryEntries.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHistoryEntries.put("visitedAtMs", new TableInfo.Column("visitedAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysHistoryEntries = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesHistoryEntries = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoHistoryEntries = new TableInfo("history_entries", _columnsHistoryEntries, _foreignKeysHistoryEntries, _indicesHistoryEntries);
        final TableInfo _existingHistoryEntries = TableInfo.read(db, "history_entries");
        if (!_infoHistoryEntries.equals(_existingHistoryEntries)) {
          return new RoomOpenHelper.ValidationResult(false, "history_entries(com.invictus.xmd.core.HistoryEntry).\n"
                  + " Expected:\n" + _infoHistoryEntries + "\n"
                  + " Found:\n" + _existingHistoryEntries);
        }
        final HashMap<String, TableInfo.Column> _columnsBookmarks = new HashMap<String, TableInfo.Column>(5);
        _columnsBookmarks.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBookmarks.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBookmarks.put("url", new TableInfo.Column("url", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBookmarks.put("faviconUrl", new TableInfo.Column("faviconUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBookmarks.put("createdAtMs", new TableInfo.Column("createdAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBookmarks = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBookmarks = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBookmarks = new TableInfo("bookmarks", _columnsBookmarks, _foreignKeysBookmarks, _indicesBookmarks);
        final TableInfo _existingBookmarks = TableInfo.read(db, "bookmarks");
        if (!_infoBookmarks.equals(_existingBookmarks)) {
          return new RoomOpenHelper.ValidationResult(false, "bookmarks(com.invictus.xmd.core.Bookmark).\n"
                  + " Expected:\n" + _infoBookmarks + "\n"
                  + " Found:\n" + _existingBookmarks);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "dd99df4f85314db83549714e2d16058e", "688afa73238d7613c104d153048373b8");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "queue_items","shortcuts","history_entries","bookmarks");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `queue_items`");
      _db.execSQL("DELETE FROM `shortcuts`");
      _db.execSQL("DELETE FROM `history_entries`");
      _db.execSQL("DELETE FROM `bookmarks`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(QueueItemDao.class, QueueItemDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ShortcutDao.class, ShortcutDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(HistoryDao.class, HistoryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(BookmarkDao.class, BookmarkDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public QueueItemDao queueItemDao() {
    if (_queueItemDao != null) {
      return _queueItemDao;
    } else {
      synchronized(this) {
        if(_queueItemDao == null) {
          _queueItemDao = new QueueItemDao_Impl(this);
        }
        return _queueItemDao;
      }
    }
  }

  @Override
  public ShortcutDao shortcutDao() {
    if (_shortcutDao != null) {
      return _shortcutDao;
    } else {
      synchronized(this) {
        if(_shortcutDao == null) {
          _shortcutDao = new ShortcutDao_Impl(this);
        }
        return _shortcutDao;
      }
    }
  }

  @Override
  public HistoryDao historyDao() {
    if (_historyDao != null) {
      return _historyDao;
    } else {
      synchronized(this) {
        if(_historyDao == null) {
          _historyDao = new HistoryDao_Impl(this);
        }
        return _historyDao;
      }
    }
  }

  @Override
  public BookmarkDao bookmarkDao() {
    if (_bookmarkDao != null) {
      return _bookmarkDao;
    } else {
      synchronized(this) {
        if(_bookmarkDao == null) {
          _bookmarkDao = new BookmarkDao_Impl(this);
        }
        return _bookmarkDao;
      }
    }
  }
}
