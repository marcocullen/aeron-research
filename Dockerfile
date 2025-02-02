FROM eclipse-temurin:21-jdk-jammy

# Pre-configure wireshark-common to avoid interactive prompt
RUN echo "wireshark-common wireshark-common/install-setuid boolean true" | debconf-set-selections

# Install all common packages in a single layer
RUN DEBIAN_FRONTEND=noninteractive apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
    wget \
    apt-transport-https \
    gnupg \
    iproute2 \
    iputils-ping \
    net-tools \
    gradle \
    git \
    build-essential \
    wireshark-common \
    tcpdump \
    iperf3 \
    sysstat \
    htop \
    curl \
    unzip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Keep container running
CMD ["/bin/bash"]