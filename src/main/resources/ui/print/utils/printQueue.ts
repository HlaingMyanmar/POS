import { DocumentType, PrintJob, PrintJobStatus, PrintOptions } from '../types/print.types';
import { fetchPdfObjectUrl } from './htmlPdfClient';

type QueueListener = (jobs: PrintJob[]) => void;

/**
 * In-memory print queue with observer pattern.
 *
 * Responsibilities:
 *  - Queue print jobs (PDF generation is async)
 *  - Track job status transitions: pending → generating → ready → printing → done
 *  - Notify React subscribers via listeners so they can re-render
 *  - Revoke object URLs when jobs are cleared to prevent memory leaks
 *
 * Usage:
 *   printQueue.add('SALE', 5, { paperSize: 'A4', design: 'STANDARD' });
 *   printQueue.subscribe(jobs => setJobs(jobs));
 */
class PrintQueue {
  private jobs: PrintJob[] = [];
  private listeners: Set<QueueListener> = new Set();
  private jobCounter = 0;

  // ── Subscribe / unsubscribe ──────────────────────────────────────────────

  subscribe(listener: QueueListener): () => void {
    this.listeners.add(listener);
    listener([...this.jobs]);               // emit current state immediately
    return () => this.listeners.delete(listener);
  }

  private emit(): void {
    const snapshot = [...this.jobs];
    this.listeners.forEach((l) => l(snapshot));
  }

  // ── Job management ────────────────────────────────────────────────────────

  /**
   * Adds a new job and immediately starts PDF generation.
   * Returns the job id so callers can track it.
   */
  add(
    documentType: DocumentType,
    documentId: number,
    options: PrintOptions
  ): string {
    const id = `job-${Date.now()}-${++this.jobCounter}`;
    const job: PrintJob = {
      id,
      documentType,
      documentId,
      options,
      status: 'pending',
      createdAt: Date.now(),
    };
    this.jobs = [...this.jobs, job];
    this.emit();
    this.generate(id);
    return id;
  }

  /** Removes a job and revokes its object URL if any. */
  remove(jobId: string): void {
    const job = this.jobs.find((j) => j.id === jobId);
    if (job?.pdfUrl) URL.revokeObjectURL(job.pdfUrl);
    this.jobs = this.jobs.filter((j) => j.id !== jobId);
    this.emit();
  }

  /** Clears all completed/errored jobs and revokes their URLs. */
  clearFinished(): void {
    this.jobs = this.jobs.filter((j) => {
      if (j.status === 'done' || j.status === 'error') {
        if (j.pdfUrl) URL.revokeObjectURL(j.pdfUrl);
        return false;
      }
      return true;
    });
    this.emit();
  }

  getJob(jobId: string): PrintJob | undefined {
    return this.jobs.find((j) => j.id === jobId);
  }

  getAll(): PrintJob[] {
    return [...this.jobs];
  }

  // ── Status helpers ────────────────────────────────────────────────────────

  markPrinting(jobId: string): void {
    this.setStatus(jobId, 'printing');
  }

  markDone(jobId: string): void {
    this.setStatus(jobId, 'done');
  }

  // ── Private ───────────────────────────────────────────────────────────────

  private async generate(jobId: string): Promise<void> {
    this.setStatus(jobId, 'generating');
    try {
      const job = this.jobs.find((j) => j.id === jobId);
      if (!job) return;

      const pdfUrl = await fetchPdfObjectUrl(
        job.documentType,
        job.documentId,
        job.options
      );

      this.jobs = this.jobs.map((j) =>
        j.id === jobId ? { ...j, status: 'ready' as PrintJobStatus, pdfUrl } : j
      );
      this.emit();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown error';
      this.jobs = this.jobs.map((j) =>
        j.id === jobId
          ? { ...j, status: 'error' as PrintJobStatus, errorMessage: message }
          : j
      );
      this.emit();
    }
  }

  private setStatus(jobId: string, status: PrintJobStatus): void {
    this.jobs = this.jobs.map((j) =>
      j.id === jobId ? { ...j, status } : j
    );
    this.emit();
  }
}

/** Singleton print queue — import and use anywhere in the app. */
export const printQueue = new PrintQueue();
