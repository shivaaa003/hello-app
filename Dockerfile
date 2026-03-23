# Use lightweight Node image
FROM node:18-alpine

# Create app directory
WORKDIR /app

# Copy dependency files first (better caching)
COPY package*.json ./

# Install dependencies
RUN npm install --only=production

# Copy app source
COPY . .

# Set environment variables
ENV PORT=3000
ENV APP_VERSION=v1

# Expose port
EXPOSE 3000

# Start app
CMD ["npm", "start"]