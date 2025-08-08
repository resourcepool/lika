// Enums
export enum PermalinkType {
  SIMPLE = 'SIMPLE',
  WITH_LANDING = 'WITH_LANDING'
}

export enum SortDirection {
  ASC = 'asc',
  DESC = 'desc'
}

export enum SortProperty {
  LAST_ACTIVITY = 'last_activity',
  CREATION_DATE = 'creation_date',
  SLUG = 'slug'
}

// Options Interfaces
export interface ActivationOptions {
  activationInstant?: string; // ISO string date
  targetUrlBeforeActivation?: string;
}

export interface LandingOptions {
  templateId?: string;
}

export interface ExpiryOptions {
  expiryDate?: string; // ISO string date (LocalDate)
}

export interface Options {
  activation?: ActivationOptions;
  landing?: LandingOptions;
  expiry?: ExpiryOptions;
}

// DTO versions for API requests (with string dates)
export interface ActivationOptionsDto {
  activationInstant?: string;
  targetUrlBeforeActivation?: string;
}

export interface LandingOptionsDto {
  templateId?: string;
}

export interface ExpiryOptionsDto {
  expiryDate?: string;
}

export interface OptionsDto {
  activation?: ActivationOptionsDto;
  landing?: LandingOptionsDto;
  expiry?: ExpiryOptionsDto;
}

// Core Models
export interface Permalink {
  id: string;
  host: string;
  ownerId: string;
  slug?: string;
  shortenedUrl: string;
  targetUrl: string;
  type: PermalinkType;
  createdDateTime: string;
  lastUpdateDateTime: string;
  options: Options;
  // Extended properties for UI
  domainHost?: string;
  domainId?: string;
  createdAt?: string; // Alias for createdDateTime for easier usage
}

export interface PermalinkCreator {
  id?: string;
  targetUrl: string;
  domainId: string;
  slug?: string;
  type: PermalinkType;
  options: OptionsDto;
}

export interface Domain {
  id?: string;
  standard: boolean;
  ownerId?: string;
  host: string;
  apiBaseUrl: string;
  landingBaseUrl?: string;
}

export interface DomainCreator {
  host: string;
  apiBaseUrl: string;
  landingBaseUrl: string;
}

export interface AdminDomainCreator {
  host: string;
  targetOwnerId?: string;
  global?: boolean;
  apiBaseUrl: string;
  landingBaseUrl: string;
}

// Account Models
export interface Account {
  id?: string;
  name: string;
  global: boolean;
  defaultAccessToken: string;
  defaultPublicReadAccessToken: string;
}

export interface AccountCreator {
  name: string;
  global: boolean;
}

// Pagination Models
export interface Sort {
  direction: SortDirection;
  property: SortProperty;
}

export interface PageRequest {
  limit: number;
  offset: number;
  filter?: string;
  sort?: Sort;
}

export interface PageResponse<T> {
  request: PageRequest;
  totalItemCount: number;
  items: T[];
}

// API Response Helpers
export interface ApiError {
  message: string;
  code?: string;
  timestamp?: string;
}
