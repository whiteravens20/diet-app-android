#!/usr/bin/env python3
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
"""Serves the test instance over HTTPS: TLS ends here, with the certificate given, and every
connection goes on in plain http to the instance behind.

    tls-proxy.py <port> <certificate> <key> <instance host> <instance port>
"""
import asyncio
import ssl
import sys


async def pipe(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    try:
        while data := await reader.read(65536):
            writer.write(data)
            await writer.drain()
    finally:
        writer.close()


async def handle(client_reader, client_writer, instance) -> None:
    try:
        reader, writer = await asyncio.open_connection(*instance)
    except OSError:
        client_writer.close()
        return
    await asyncio.gather(pipe(client_reader, writer), pipe(reader, client_writer), return_exceptions=True)


async def main(port: int, certificate: str, key: str, instance: tuple) -> None:
    context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
    context.load_cert_chain(certificate, key)
    server = await asyncio.start_server(lambda r, w: handle(r, w, instance), "127.0.0.1", port, ssl=context)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    port, certificate, key, host, instance_port = sys.argv[1:6]
    asyncio.run(main(int(port), certificate, key, (host, int(instance_port))))
