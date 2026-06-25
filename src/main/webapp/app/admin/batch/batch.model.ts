export interface StepExecution {
  stepName: string;
  status: string;
  readCount: number;
  writeCount: number;
  skipCount: number;
  commitCount: number;
}

export interface JobExecution {
  executionId: number;
  instanceId: number;
  jobName: string;
  status: string;
  exitCode?: string;
  exitDescription?: string;
  createTime?: string;
  startTime?: string;
  endTime?: string;
  durationMs?: number;
  steps: StepExecution[];
}

export interface JobSummary {
  name: string;
  lastStatus?: string;
  lastStartTime?: string;
  runningCount: number;
}
