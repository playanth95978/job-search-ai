import dayjs from 'dayjs/esm';

export interface IWorkspace {
  id: string | null;
  rootPath?: string | null;
  repoFullName?: string | null;
  branch?: string | null;
}

export interface IConversation {
  id: string;
  userId?: string | null;
  title?: string | null;
  createdAt?: dayjs.Dayjs | null;
  lastMessageAt?: dayjs.Dayjs | null;
  summary?: string | null;
  metadata?: string | null;
  version?: number | null;
  workspace?: IWorkspace | null;
  typeChat?: string | null;
}

export type NewConversation = Omit<IConversation, 'id'> & { id: null };
