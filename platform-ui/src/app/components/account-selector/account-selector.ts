import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { AccountService } from '../../services/account.service';

// every trading account is an individual brokerage account, shown by the last 4 characters of its id
export const ACCOUNT_TYPE = 'Individual brokerage';

export function accountNumber(accountId: string): string {
  return `•••• ${accountId.slice(-4)}`;
}

// lists the logged-in user's trading accounts (GET /v1/clients/me); choosing one switches every page to it
@Component({
  selector: 'app-account-selector',
  imports: [],
  templateUrl: './account-selector.html',
  styleUrl: './account-selector.css'
})
export class AccountSelector {
  protected account = inject(AccountService);
  protected readonly accountType = ACCOUNT_TYPE;
  protected readonly accountNumber = accountNumber;

  isOpen = false;

  protected selectedLabel = computed(() => {
    const accountId = this.account.accountId();
    return accountId ? accountNumber(accountId) : 'No account yet';
  });

  toggle(): void {
    this.isOpen = !this.isOpen;
  }

  selectAccount(accountId: string): void {
    this.account.selectedAccountId.set(accountId);
    this.isOpen = false;
  }

  // signals, because they change after an await (the app runs without zone.js)
  protected opening = signal(false);
  protected openError = signal('');

  private confirmDialog = viewChild.required<ElementRef<HTMLDialogElement>>('confirmDialog');

  // "Open new account" only asks; the account is created when the user confirms
  askToOpenAccount(): void {
    this.openError.set('');
    this.isOpen = false;
    this.confirmDialog().nativeElement.showModal();
  }

  cancelOpenAccount(): void {
    if (!this.opening()) {
      this.confirmDialog().nativeElement.close();
    }
  }

  // Escape fires "cancel"; route it through cancelOpenAccount so it can't close mid-request
  onDialogCancel(event: Event): void {
    event.preventDefault();
    this.cancelOpenAccount();
  }

  // a click on the dialog element itself (not its content) is a click on the backdrop
  onDialogClick(event: MouseEvent): void {
    if (event.target === this.confirmDialog().nativeElement) {
      this.cancelOpenAccount();
    }
  }

  async confirmOpenAccount(): Promise<void> {
    if (this.opening()) {
      return;
    }
    this.opening.set(true);
    this.openError.set('');
    try {
      await this.account.openAdditionalAccount();
      this.confirmDialog().nativeElement.close();
    } catch {
      this.openError.set("Couldn't open a new account. Please try again.");
    } finally {
      this.opening.set(false);
    }
  }
}
