package org.tron.p2p.dns.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


@Slf4j(topic = "net")
public class LinkCache {

  Map<String, Set<String>> backrefs;
  @Getter
  @Setter
  private boolean changed; //if data in backrefs changes, we need to rebuild trees

  public LinkCache() {
    backrefs = new HashMap<>();
    changed = false;
  }

  public boolean isReferenced(String urlScheme) {
    return backrefs.containsKey(urlScheme) && !backrefs.get(urlScheme).isEmpty();
  }

  // there is a link `to` in tree `from`
  public void addLink(String from, String to) {
    if (backrefs.containsKey(to)) {
      Set<String> refs = backrefs.get(to);
      if (!refs.contains(from)) {
        changed = true;
      }
      refs.add(from);
    } else {
      Set<String> refs = new HashSet<>();
      refs.add(from);
      backrefs.put(to, refs);
      changed = true;
    }
  }

  //clears all links of the given tree.
  public void resetLinks(String from, final Set<String> keep) {
    List<String> stk = new ArrayList<>();
    stk.add(from);

    while (!stk.isEmpty()) {
      int size = stk.size();
      String item = stk.get(size - 1);
      stk = stk.subList(0, size - 1);

      Iterator<Entry<String, Set<String>>> it = backrefs.entrySet().iterator();
      while (it.hasNext()) {
        Entry<String, Set<String>> entry = it.next();
        String r = entry.getKey();
        Set<String> refs = entry.getValue();
        if (keep.contains(r)) {
          continue;
        }
        if (!refs.contains(item)) {
          continue;
        }
        this.changed = true;
        refs.remove(item);
        if (refs.isEmpty()) {
          it.remove();
          stk.add(r);
        }
      }
    }
  }
}
