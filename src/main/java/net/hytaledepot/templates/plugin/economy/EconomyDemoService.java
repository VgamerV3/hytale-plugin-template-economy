package net.hytaledepot.templates.plugin.economy;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class EconomyDemoService {
  private final Map<String, AtomicLong> actionCounters = new ConcurrentHashMap<>();
  private final Map<String, String> lastActionBySender = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> balances = new ConcurrentHashMap<>();
  private final Deque<String> ledger = new ArrayDeque<>();
  private volatile Path dataDirectory;

  public void initialize(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    balances.computeIfAbsent("treasury", key -> new AtomicLong(500));
  }

  public void onHeartbeat(long tick) {
    actionCounters.computeIfAbsent("heartbeat", key -> new AtomicLong()).incrementAndGet();

  }

  public void recordExternalEvent(String key) {
    actionCounters.computeIfAbsent(String.valueOf(key), item -> new AtomicLong()).incrementAndGet();
  }

  public String applyAction(EconomyPluginState state, String sender, String action, long heartbeatTicks) {
    String normalizedSender = String.valueOf(sender == null ? "unknown" : sender);
    String normalizedAction = normalizeAction(action);

    actionCounters.computeIfAbsent(normalizedAction, key -> new AtomicLong()).incrementAndGet();
    lastActionBySender.put(normalizedSender, normalizedAction);

    if ("toggle".equals(normalizedAction)) {
      boolean enabled = state.toggleDemoFlag();
      return "[Economy] demoFlag=" + enabled + ", heartbeatTicks=" + heartbeatTicks;
    }

    if ("info".equals(normalizedAction)) {
      return "[Economy] " + diagnostics();
    }

    String domainResult = handleDomainAction(normalizedSender, normalizedAction, heartbeatTicks);
    if (domainResult != null) {
      return "[Economy] " + domainResult;
    }

    return "[Economy] unknown action='" + normalizedAction + "' (try: info, toggle, sample, credit-demo, transfer-demo, balance-demo)";
  }

  public String describeLastAction(String sender) {
    return lastActionBySender.getOrDefault(String.valueOf(sender), "none");
  }

  public long operationCount() {
    long total = 0;
    for (AtomicLong value : actionCounters.values()) {
      total += value.get();
    }
    return total;
  }

  public String diagnostics() {
    String directory = dataDirectory == null ? "unset" : dataDirectory.toString();
    return "ops=" + operationCount()
        + ", accounts=" + balances.size()
        + ", ledgerEntries=" + ledger.size()
        + ", treasury=" + balanceOf("treasury")
        + ", dataDirectory=" + directory;
  }

  public void shutdown() {
    ledger.clear();
  }

  private String handleDomainAction(String sender, String action, long heartbeatTicks) {
    if ("sample".equals(action) || "credit-demo".equals(action)) {
      long balance = balanceRef(sender).addAndGet(25);
      appendLedger("credit", sender, 25, balance);
      return "credited 25 coins, balance=" + balance;
    }
    if ("transfer-demo".equals(action)) {
      AtomicLong senderBalance = balanceRef(sender);
      if (senderBalance.get() < 10) {
        return "transfer blocked, balance=" + senderBalance.get() + " (need >=10)";
      }
      long nextSender = senderBalance.addAndGet(-10);
      long treasury = balanceRef("treasury").addAndGet(10);
      appendLedger("transfer", sender, 10, nextSender);
      return "transferred 10 to treasury, senderBalance=" + nextSender + ", treasury=" + treasury;
    }
    if ("balance-demo".equals(action)) {
      return "balance=" + balanceOf(sender) + ", treasury=" + balanceOf("treasury");
    }
    return null;
  }

  private AtomicLong balanceRef(String account) {
    return balances.computeIfAbsent(String.valueOf(account).toLowerCase(), key -> new AtomicLong());
  }

  private long balanceOf(String account) {
    return balanceRef(account).get();
  }

  private void appendLedger(String kind, String account, long amount, long resultingBalance) {
    ledger.addLast(kind + ":" + account + ":" + amount + ":" + resultingBalance);
    while (ledger.size() > 24) {
      ledger.removeFirst();
    }
  }

  private static String normalizeAction(String action) {
    String normalized = String.valueOf(action == null ? "" : action).trim().toLowerCase();
    return normalized.isEmpty() ? "sample" : normalized;
  }
}
