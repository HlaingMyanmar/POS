package org.sspd.servicemgmt.servicejoboptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceJobServiceSettleSerialMappingTest {

    @Test
    void rejectsSecondSettlement() {
        ServiceJob settled = ServiceJob.builder()
                .status(ServiceJobStatus.COMPLETED)
                .leadFinalCheckStatus(true)
                .finalApprovalStatus(true)
                .paymentStatus(org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus.Paid)
                .voided(false)
                .build();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ServiceJobService.assertReadyForSettlement(settled));

        assertEquals("This service job has already been settled", error.getMessage());
    }

    @Test
    void allowsApprovedCompletedJobToSettleOnce() {
        ServiceJob ready = ServiceJob.builder()
                .status(ServiceJobStatus.COMPLETED)
                .leadFinalCheckStatus(true)
                .finalApprovalStatus(true)
                .build();

        assertDoesNotThrow(() -> ServiceJobService.assertReadyForSettlement(ready));
    }

    @Test
    void allowsResettleAfterVoid() {
        ServiceJob voided = ServiceJob.builder()
                .status(ServiceJobStatus.COMPLETED)
                .leadFinalCheckStatus(true)
                .finalApprovalStatus(true)
                .paymentStatus(org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus.Paid)
                .voided(true)
                .build();

        assertDoesNotThrow(() -> ServiceJobService.assertReadyForSettlement(voided));
    }

    @Test
    void rejectsSettleWithoutFinalChecks() {
        ServiceJob missingLead = ServiceJob.builder()
                .status(ServiceJobStatus.COMPLETED)
                .leadFinalCheckStatus(false)
                .finalApprovalStatus(true)
                .build();
        ServiceJob missingSupervisor = ServiceJob.builder()
                .status(ServiceJobStatus.COMPLETED)
                .leadFinalCheckStatus(true)
                .finalApprovalStatus(false)
                .build();

        assertThrows(IllegalStateException.class, () -> ServiceJobService.assertReadyForSettlement(missingLead));
        assertThrows(IllegalStateException.class, () -> ServiceJobService.assertReadyForSettlement(missingSupervisor));
    }

    @Test
    void rejectsSettleWhenNotCompleted() {
        ServiceJob inProgress = ServiceJob.builder()
                .status(ServiceJobStatus.IN_PROGRESS)
                .leadFinalCheckStatus(true)
                .finalApprovalStatus(true)
                .build();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ServiceJobService.assertReadyForSettlement(inProgress));
        assertEquals("Job must be completed before settlement", error.getMessage());
    }

    @Test
    void rejectsEditingCompletedOrSettledJobs() {
        ServiceJob completed = ServiceJob.builder()
                .status(ServiceJobStatus.COMPLETED)
                .build();
        ServiceJob settled = ServiceJob.builder()
                .status(ServiceJobStatus.IN_PROGRESS)
                .paymentStatus(org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus.Partial)
                .voided(false)
                .build();

        assertThrows(IllegalStateException.class, () -> ServiceJobService.assertEditable(completed));
        assertThrows(IllegalStateException.class, () -> ServiceJobService.assertEditable(settled));
    }

    @Test
    void rejectsEditingDeliveredOrCancelledJobs() {
        assertThrows(IllegalStateException.class,
                () -> ServiceJobService.assertEditable(ServiceJob.builder().status(ServiceJobStatus.DELIVERED).build()));
        assertThrows(IllegalStateException.class,
                () -> ServiceJobService.assertEditable(ServiceJob.builder().status(ServiceJobStatus.CANCELLED).build()));
    }

    @Test
    void allowsEditingOpenJob() {
        ServiceJob open = ServiceJob.builder()
                .status(ServiceJobStatus.IN_PROGRESS)
                .build();
        assertDoesNotThrow(() -> ServiceJobService.assertEditable(open));
    }

    @Test
    void jobNoUsesPersistedId() {
        assertEquals("SJ-000001", ServiceJobService.generateJobNo(1));
        assertEquals("SJ-000042", ServiceJobService.generateJobNo(42));
        assertEquals("SJ-123456", ServiceJobService.generateJobNo(123456));
    }
}
