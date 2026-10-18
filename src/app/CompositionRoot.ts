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
  // Telemetry must be available even when the host handshake fails.
  logger.setSink(events => nativeGateway.emitLogs(events));
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
    async bootstrap(flowId: string) {
      logger.log('info', 'bootstrap', 'bootstrap.started', { flowId });
      await nativeGateway.bootstrap(flowId);
      logger.log('info', 'bootstrap', 'bootstrap.completed', { flowId });
    },
    dispose() {
      home.deactivate();
      runtime.dispose();
      logger.dispose();
    },
  };
}
