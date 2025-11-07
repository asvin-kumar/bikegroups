FROM node:18-bullseye-slim

WORKDIR /app

# Install minimal system packages with security updates, then clean apt caches
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends ca-certificates curl tar \
    && rm -rf /var/lib/apt/lists/*

# Download and install babashka (static binary)
RUN curl -sSL -o /tmp/bb.tar.gz https://github.com/babashka/babashka/releases/download/v1.4.192/babashka-1.4.192-linux-amd64-static.tar.gz \
    && tar -xzf /tmp/bb.tar.gz -C /tmp \
    && mv /tmp/bb /usr/local/bin/ \
    && rm /tmp/bb.tar.gz

# Copy package.json and package-lock.json to leverage Docker cache
COPY package*.json ./

# Use npm ci for reproducible installs and skip audit to avoid network time during build
RUN npm ci --no-audit --prefer-offline

COPY . .

RUN bb download-data
RUN bb build

EXPOSE 8788

CMD ["bb", "dev-server"]
