FROM hseeberger/scala-sbt:11.0.12-1.5.5_2.13.6

# Create a non-root user
RUN useradd -m -s /bin/bash coderunner

# Set up working directory
WORKDIR /code
RUN chown coderunner:coderunner /code

# Switch to non-root user
USER coderunner

# Copy entrypoint script
COPY --chown=coderunner:coderunner entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"] 