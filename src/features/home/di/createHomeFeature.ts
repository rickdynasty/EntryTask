import { createHomeRepository } from '../data/HomeRepositoryImpl';
import { HomeViewModel } from '../presentation/HomeViewModel';
import { clientActions, type AppStateStore } from '@infra/state/createAppStore';
import type { LoggerPort } from '@platform/ports/LoggerPort';

export function createHomeFeature(
  runtime: AppStateStore,
  logger: LoggerPort,
): HomeViewModel {
  return new HomeViewModel(
    createHomeRepository(runtime),
    {
      selectedService: () =>
        runtime.store.getState().homeClient.selectedService,
      selectService: id => {
        runtime.store.dispatch(clientActions.selectService(id));
      },
      subscribe: listener => runtime.store.subscribe(listener),
    },
    logger,
  );
}
