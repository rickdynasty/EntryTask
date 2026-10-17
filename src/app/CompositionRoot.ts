import { Logger } from '@infra/logging/Logger';
import { defaultEndpointRegistry } from '@infra/network/EndpointRegistry';
import { TransportRouter } from '@infra/network/TransportRouter';
import { NativeTransport } from '@infra/network/transports/NativeTransport';
import { RnHttpTransport } from '@infra/network/transports/RnHttpTransport';
import { createAppStore } from '@infra/state/createAppStore';
import { createHomeFeature } from '@features/home/public';
import { nativeGateway } from '@platform/native/NativeGateway';

export function createApplication() {
  const logger = new Logger(__DEV__);
  const nativeTransport = new NativeTransport();
  const network = new TransportRouter(
    defaultEndpointRegistry,
    {
      nativeMock: nativeTransport,
      native: nativeTransport,
      rnHttp: new RnHttpTransport(),
    },
    logger,
  );
  const runtime = createAppStore(network);
  const home = createHomeFeature(runtime, logger);
  return {
    logger,
    home,
    async bootstrap() {
      logger.log('info', 'bootstrap', 'bootstrap.started');
      await nativeGateway.bootstrap();
      logger.setSink(events => nativeGateway.emitLogs(events));
      logger.log('info', 'bootstrap', 'bootstrap.completed');
    },
    dispose() {
      home.deactivate();
      runtime.dispose();
      logger.dispose();
    },
  };
}
