import dayjs from 'dayjs/esm';

import { IConversation, NewConversation } from './conversation.model';

export const sampleWithRequiredData: IConversation = {
  id: '342303f0-5a73-4e69-914f-1b77e248395d',
  userId: 'quelquefois',
  createdAt: dayjs('2026-03-27T09:43'),
};

export const sampleWithPartialData: IConversation = {
  id: 'e9705548-268d-451f-acc0-8e6d443a9fcb',
  userId: 'souple commis demain',
  createdAt: dayjs('2026-03-27T12:10'),
  metadata: '../fake-data/blob/hipster.txt',
};

export const sampleWithFullData: IConversation = {
  id: '80f69821-3dc1-48f7-ba4e-b280074727e4',
  userId: 'vouh',
  title: 'corps enseignant',
  createdAt: dayjs('2026-03-27T14:27'),
  lastMessageAt: dayjs('2026-03-27T20:01'),
  summary: '../fake-data/blob/hipster.txt',
  metadata: '../fake-data/blob/hipster.txt',
};

export const sampleWithNewData: NewConversation = {
  userId: 'orange tic-tac pas mal',
  createdAt: dayjs('2026-03-28T05:31'),
  id: null,
};

Object.freeze(sampleWithNewData);
Object.freeze(sampleWithRequiredData);
Object.freeze(sampleWithPartialData);
Object.freeze(sampleWithFullData);
