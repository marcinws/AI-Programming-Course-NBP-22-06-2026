export type PendingState = 'IDLE' | 'SUBMITTING' | 'STREAMING' | 'ERROR';

export interface DisplayMessage {
  role: string;
  content: string;
  createdAt: Date;
  isDecision: boolean;
  isStreaming: boolean;
}
