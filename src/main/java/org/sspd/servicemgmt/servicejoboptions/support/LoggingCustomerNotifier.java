package org.sspd.servicemgmt.servicejoboptions.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;

/**
 * Default notifier: records intent in application logs only.
 * Replace or decorate this bean when a real messaging provider is wired.
 */
@Component
public class LoggingCustomerNotifier implements CustomerNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingCustomerNotifier.class);

    @Override
    public void dispatch(ServiceJob job, String channel, String note, String actor) {
        String phone = job.getCustomer() != null ? job.getCustomer().getPhone() : null;
        log.info("Customer notify logged only (no provider): jobNo={} channel={} phone={} actor={} note={}",
                job.getJobNo(), channel, phone, actor, note);
    }
}
