/*
 * Copyright (c) 2016 - 2026 Farooq Khan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.jsondb.events;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.jsondb.CollectionMetaData;
import io.jsondb.JsonDBConfig;
import io.jsondb.JsonDBException;

/**
 * A class that holds a list of CollectionFileChangeListeners.
 * @version 1.0 15-Oct-2016
 */
public class EventListenerList {
  private Logger logger = LoggerFactory.getLogger(EventListenerList.class);

  private JsonDBConfig dbConfig = null;
  private Map<String, CollectionMetaData> cmdMap;

  private volatile List<CollectionFileChangeListener> listeners;
  private ExecutorService collectionFilesWatcherExecutor;
  private WatchService watcher = null;
  private volatile boolean stopWatcher;

  public EventListenerList(JsonDBConfig dbConfig, Map<String, CollectionMetaData> cmdMap) {
    this.dbConfig = dbConfig;
    this.cmdMap = cmdMap;
  }

  public void addCollectionFileChangeListener(CollectionFileChangeListener listener) {
    if (null == listeners) {
      // The watcher thread reads this list while listeners are added or removed
      listeners = new CopyOnWriteArrayList<CollectionFileChangeListener>();
      stopWatcher = false;

      listeners.add(listener);

      collectionFilesWatcherExecutor = Executors.newSingleThreadExecutor(
          new ThreadFactoryBuilder().setNameFormat("jsondb-files-watcher-thread-%d").build());

      try {
        watcher = dbConfig.getDbFilesPath().getFileSystem().newWatchService();
        dbConfig.getDbFilesPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                                                    StandardWatchEventKinds.ENTRY_DELETE,
                                                    StandardWatchEventKinds.ENTRY_MODIFY);
      } catch (IOException e) {
        throw new JsonDBException("Failed to create the WatchService for the dbFiles location", e);
      }

      collectionFilesWatcherExecutor.execute(new CollectionFilesWatcherRunnable());
    } else {
      listeners.add(listener);
    }
  }

  public void removeCollectionFileChangeListener(CollectionFileChangeListener listener) {
    if (null == listeners) {
      return;
    }
    listeners.remove(listener);
    if (listeners.isEmpty()) {
      stopWatching();
    }
  }

  public boolean hasCollectionFileChangeListener() {
    if ((null != listeners) && (listeners.size() > 0)) {
      return true;
    }
    return false;
  }

  public void shutdown() {
    if (null != listeners) {
      stopWatching();
    }
  }

  /**
   * The listeners to notify; read once per event because the last listener can be removed meanwhile
   */
  private List<CollectionFileChangeListener> currentListeners() {
    List<CollectionFileChangeListener> current = listeners;
    return null == current ? Collections.<CollectionFileChangeListener>emptyList() : current;
  }

  /**
   * Stops the watcher thread. Clearing the list lets a later addCollectionFileChangeListener() start a
   * new watcher.
   */
  private void stopWatching() {
    stopWatcher = true;
    collectionFilesWatcherExecutor.shutdownNow();
    try {
      watcher.close();
    } catch (IOException e) {
      logger.error("Failed to close the WatchService for the dbFiles location", e);
    }
    listeners = null;
  }


  private class CollectionFilesWatcherRunnable implements Runnable {
    @Override
    public void run() {
      while (!stopWatcher) {
        WatchKey watckKey = null;
        try {
          watckKey = watcher.take();
        } catch (InterruptedException e) {
          logger.debug("The watcher service thread was interrupted", e);
          return;
        }
        List<WatchEvent<?>> events = watckKey.pollEvents();
        for (WatchEvent<?> event : events) {
          WatchEvent.Kind<?> kind = event.kind();
          if (kind == StandardWatchEventKinds.OVERFLOW) {
            continue;
          }
          @SuppressWarnings("unchecked")
          WatchEvent<Path> ev = (WatchEvent<Path>)event;
          String fileName = ev.context().getFileName().toString();
          int extnLocation = fileName.lastIndexOf('.');
          if(extnLocation != -1) {
            String collectionName = fileName.substring(0, extnLocation);
            if (fileName.endsWith(".json") && (cmdMap.containsKey(collectionName))) {
              if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                for (CollectionFileChangeListener listener : currentListeners()) {
                  listener.collectionFileAdded(collectionName);
                }
              } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                for (CollectionFileChangeListener listener : currentListeners()) {
                  listener.collectionFileDeleted(collectionName);
                }
              } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                for (CollectionFileChangeListener listener : currentListeners()) {
                  listener.collectionFileModified(collectionName);
                }
              }
            }
          }
        }
      }
    }
  }
}
