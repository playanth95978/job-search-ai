import { HttpHeaders } from '@angular/common/http';
import { Component, computed, effect, inject, input, OnInit, output, signal, WritableSignal } from '@angular/core';
import { FormArray, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Data, ParamMap, Router, RouterLink, RouterLinkActive } from '@angular/router';

import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap/modal';
import { TranslateModule } from '@ngx-translate/core';
import { combineLatest, filter, Subscription, tap } from 'rxjs';

import { DEFAULT_SORT_DATA, ITEM_DELETED_EVENT, SORT } from 'app/config/navigation.constants';
import { ITEMS_PER_PAGE } from 'app/config/pagination.constants';
import { DataUtils } from 'app/core/util/data-util.service';
import { ParseLinks } from 'app/core/util/parse-links.service';
import { SortService, type SortState, sortStateSignal } from 'app/shared/sort';
import { IConversation } from '../conversation.model';
import { ConversationDeleteDialog } from '../delete/conversation-delete-dialog';
import { ConversationService } from '../service/conversation.service';
import TranslateDirective from '../../../shared/language/translate.directive';
import { KeyValuePipe } from '@angular/common';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { ConversationFormService } from '../conversation-form.service';
import { InfiniteScrollDirective } from 'ngx-infinite-scroll';

@Component({
  selector: 'jhi-conversation',
  styleUrls: ['./conversation.scss'],
  templateUrl: './conversation.html',
  host: {
    '[class.theme-light]': "theme() === 'light'",
  },
  imports: [
    RouterLink,
    FormsModule,
    FontAwesomeModule,
    TranslateModule,
    RouterLinkActive,
    TranslateDirective,
    KeyValuePipe,
    NgbDropdown,
    NgbDropdownMenu,
    NgbDropdownToggle,
    NgbDropdownItem,
    ReactiveFormsModule,
    InfiniteScrollDirective,
  ],
})
export class Conversation implements OnInit {
  /**
   * Optional router prefix used to build the per-item link. Defaults to {@code /chat}
   * so existing usages stay unchanged; the Jira agent passes {@code /jira-agent} to
   * keep navigation inside its own page.
   */
  readonly routePrefix = input<string>('/chat');
  readonly typeChat = input<string | null>(null);

  /**
   * When true, clicking an item emits {@link conversationSelected} instead of navigating
   * (used to load a conversation into an existing chat, e.g. the interview page).
   */
  readonly selectable = input<boolean>(false);
  /** Id of the currently selected conversation (for highlight in selectable mode). */
  readonly activeId = input<string | null>(null);
  /** Visual theme: 'dark' (sidebar, default) or 'light' (embedded in a light panel). */
  readonly theme = input<'dark' | 'light'>('dark');
  readonly conversationSelected = output<IConversation>();

  subscription: Subscription | null = null;
  readonly conversations = signal<IConversation[]>([]);
  readonly conversationsGroupByCreatedDate = signal<Record<string, IConversation[]>>({});
  readonly conversationsFormGroupByCreatedDate = signal<Record<string, FormGroup[]>>({});

  sortState = sortStateSignal({});

  readonly itemsPerPage = signal(ITEMS_PER_PAGE);
  readonly links: WritableSignal<Record<string, undefined | Record<string, string | undefined>>> = signal({});
  readonly hasMorePage = computed(() => !!this.links().next);
  readonly isFirstFetch = computed(() => Object.keys(this.links()).length === 0);

  readonly router = inject(Router);
  protected readonly conversationService = inject(ConversationService);
  // eslint-disable-next-line @typescript-eslint/member-ordering
  readonly isLoading = this.conversationService.conversationsResource.isLoading;

  protected readonly activatedRoute = inject(ActivatedRoute);
  protected readonly sortService = inject(SortService);
  protected parseLinks = inject(ParseLinks);
  protected dataUtils = inject(DataUtils);
  protected modalService = inject(NgbModal);
  protected canRename = signal<number | null>(null);

  private readonly conversationFormService = inject(ConversationFormService);
  // eslint-disable-next-line @typescript-eslint/member-ordering
  protected readonly conversationsFormGroupArray = this.conversationFormService.createConversationArrayFormGroup();

  constructor() {
    effect(() => {
      const headers = this.conversationService.conversationsResource.headers();
      if (headers) {
        this.fillComponentAttributesFromResponseHeader(headers);
      }
    });
    effect(() => {
      this.conversations.update(conversations =>
        this.fillComponentAttributesFromResponseBody([...this.conversationService.conversations()], conversations),
      );
      this.conversationsGroupByCreatedDate.set(this.conversationService.getConversationByCreatedDate(this.conversations()));
      let cvFiltered = [];
      cvFiltered = this.conversations().filter(c => c.id !== '');

      this.conversationFormArray().clear();
      for (const cv of cvFiltered) {
        if (cv.id) {
          this.conversationFormArray().push(this.conversationFormService.createConversationFormGroup(cv));
        }
      }
      this.conversationsFormGroupByCreatedDate.set(this.getConversationFormGroupByCreatedDate(this.conversations()));
    });
  }

  getConversationFormGroupByCreatedDate(conversations: IConversation[] | null | undefined): Record<string, FormGroup[]> {
    return (conversations ?? []).reduce<Record<string, FormGroup[]>>((acc, item) => {
      const key = item.createdAt?.format('D MMM YYYY');
      if (key) {
        acc[key] = acc[key] ?? [];
        acc[key].push(this.conversationFormService.createConversationFormGroup(item));
      }
      return acc;
    }, {});
  }

  trackId = (item: IConversation): string => this.conversationService.getConversationIdentifier(item);

  /** In selectable mode, emit the clicked conversation so the host can load it. */
  onSelect(id: string | undefined | null): void {
    if (!this.selectable() || !id) {
      return;
    }
    const conversation = this.conversations().find(c => c.id === id);
    if (conversation) {
      this.conversationSelected.emit(conversation);
    }
  }

  ngOnInit(): void {
    this.subscription = combineLatest([this.activatedRoute.queryParamMap, this.activatedRoute.data])
      .pipe(
        tap(([params, data]) => this.fillComponentAttributeFromRoute(params, data)),
        tap(() => this.reset()),
        tap(() => this.load()),
      )
      .subscribe();
  }

  reset(): void {
    this.conversations.set([]);
  }

  loadNextPage(): void {
    this.load();
  }

  byteSize(base64String: string): string {
    return this.dataUtils.byteSize(base64String);
  }

  openFile(base64String: string, contentType: string | null | undefined): void {
    return this.dataUtils.openFile(base64String, contentType);
  }

  conversationFormArray(): FormArray {
    return this.conversationsFormGroupArray.get('conversations') as unknown as FormArray;
  }

  delete(i: number): void {
    const conversationIndex = this.conversationFormArray().at(i) as FormGroup;
    const modalRef = this.modalService.open(ConversationDeleteDialog, { size: 'lg', backdrop: 'static' });
    modalRef.componentInstance.conversation = conversationIndex.getRawValue() as IConversation;
    // unsubscribe not needed because closed completes on modal close
    modalRef.closed
      .pipe(
        filter(reason => reason === ITEM_DELETED_EVENT),
        tap(() => this.load()),
      )
      .subscribe();
  }

  load(): void {
    this.queryBackend();
  }

  navigateToWithComponentValues(event: SortState): void {
    this.handleNavigation(event);
  }

  protected fillComponentAttributeFromRoute(params: ParamMap, data: Data): void {
    this.sortState.set(this.sortService.parseSortParam(params.get(SORT) ?? data[DEFAULT_SORT_DATA]));
  }

  protected fillComponentAttributesFromResponseBody(data: IConversation[], currentValue: IConversation[]): IConversation[] {
    const conversationsNew = [...currentValue];
    for (const d of data) {
      if (!conversationsNew.some(op => op.id === d.id)) {
        conversationsNew.push(d);
      }
    }
    return conversationsNew;
  }

  protected fillComponentAttributesFromResponseHeader(headers: HttpHeaders): void {
    const linkHeader = headers.get('link');
    if (linkHeader) {
      this.links.set(this.parseLinks.parseAll(linkHeader));
    } else {
      this.links.set({});
    }
  }

  protected queryBackend(): void {
    const queryObject: any = {
      size: this.itemsPerPage(),
    };
    if (this.typeChat()) {
      queryObject['typeChat'] = this.typeChat();
    }
    if (this.hasMorePage()) {
      Object.assign(queryObject, this.links().next);
    } else if (this.isFirstFetch()) {
      Object.assign(queryObject, { sort: this.sortService.buildSortParam(this.sortState()) });
    }

    this.conversationService.conversationsParams.set(queryObject);
  }

  protected handleNavigation(sortState: SortState): void {
    this.links.set({});

    const queryParamsObj = {
      sort: this.sortService.buildSortParam(sortState),
    };

    this.router.navigate(['./'], {
      relativeTo: this.activatedRoute,
      queryParams: queryParamsObj,
    });
  }

  protected rename(i: number): void {
    this.canRename.set(i);
  }

  protected update(i: number): void {
    const conversationIndex = this.conversationFormArray().at(i) as FormGroup;
    const conversation = this.conversationFormService.getConversation(conversationIndex) as IConversation;
    this.conversationService.partialUpdate(conversation).subscribe(() => this.load());
  }
}
