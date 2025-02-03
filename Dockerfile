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

RUN wget -O aeron-agent-1.47.2.jar https://repo1.maven.org/maven2/io/aeron/aeron-agent/1.47.2/aeron-agent-1.47.2.jar

# Keep container running
CMD ["/bin/bash"]