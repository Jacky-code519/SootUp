package sootup.core.cache.provider;

import sootup.core.cache.Cache;
import sootup.core.cache.FullCache;
import sootup.core.model.SootClass;

public class FullCacheProvider<S extends SootClass<?>> implements CacheProvider<S> {

  @Override
  public Cache<S> createCache() {
    return new FullCache<S>();
  }
}
