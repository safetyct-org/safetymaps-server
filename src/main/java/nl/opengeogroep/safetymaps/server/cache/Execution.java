package nl.opengeogroep.safetymaps.server.cache;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Execution {
  private static ExecutorService _service;
  private static ArrayBlockingQueue<Runnable> _boundedQueue;

  public final static ExecutorService GetService() {
    if (_service == null) {
      _boundedQueue = new ArrayBlockingQueue<Runnable>(1000);
      _service = new ThreadPoolExecutor(10, 20, 60, TimeUnit.SECONDS, _boundedQueue, new ThreadPoolExecutor.AbortPolicy());
    }

    return _service;
  } 
}
