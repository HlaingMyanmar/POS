package org.sspd.servicemgmt.servicejoboptions.support;

import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;

/**
 * Pluggable customer notification delivery (SMS / Viber / Telegram / etc.).
 * History is always persisted by {@code ServiceJobService.notifyCustomer};
 * implementations handle outward delivery only.
 */
public interface CustomerNotifier {

    void dispatch(ServiceJob job, String channel, String note, String actor);
}
