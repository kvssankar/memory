# AI Edge Gallery - Basic Functionality Guide

## Overview

AI Edge Gallery is an Android application that demonstrates on-device AI capabilities using Google's MediaPipe and TensorFlow Lite frameworks. The app allows users to run various AI models directly on their mobile devices without requiring internet connectivity.

## Core Features

### 1. Large Language Model (LLM) Capabilities

#### AI Chat (`LLM_CHAT`)
- **Purpose**: Interactive conversational AI using on-device LLMs
- **Features**:
  - Real-time text-based conversations
  - Configurable model parameters (temperature, top-k, top-p)
  - Session management with reset capability
  - Performance metrics display (tokens/sec, latency)
- **Usage**: Navigate to AI Chat → Select a model → Start typing messages

#### Ask Image (`LLM_ASK_IMAGE`)
- **Purpose**: Visual question answering using vision-enabled LLMs
- **Features**:
  - Upload up to 10 images per session
  - Take photos directly from camera
  - Ask questions about image content
  - Multi-image analysis support
- **Usage**: Navigate to Ask Image → Add images via + button → Type questions about the images

#### Audio Scribe (`LLM_ASK_AUDIO`)
- **Purpose**: Audio transcription and translation using LLMs
- **Features**:
  - Record audio clips (up to 30 seconds)
  - Transcribe speech to text
  - Translate audio content
  - Audio playback controls
- **Usage**: Navigate to Audio Scribe → Record audio → Get transcription/translation

#### Prompt Lab (`LLM_PROMPT_LAB`)
- **Purpose**: Single-turn prompt experimentation
- **Features**:
  - Prompt templates library
  - Custom prompt creation
  - Split-view interface (input/output)
  - Benchmark testing capabilities
- **Usage**: Navigate to Prompt Lab → Enter prompts → View responses

### 2. Model Management

#### Model Download & Installation
- **Features**:
  - Browse available models by category
  - Download models on-demand
  - Progress tracking for downloads
  - Storage management
- **Usage**: Select a task → Choose model → Download automatically

#### Custom Model Import
- **Features**:
  - Import models from local storage
  - Configure imported model settings
  - Custom model parameters
- **Usage**: Home screen → Settings → Import model → Select local file

#### Model Configuration
- **Available Settings**:
  - Temperature (0.0 - 2.0): Controls randomness
  - Top-K (1-100): Limits vocabulary consideration
  - Top-P (0.0 - 1.0): Nucleus sampling parameter
  - Max Tokens: Maximum response length
  - Accelerator: CPU vs GPU selection

### 3. User Interface Components

#### Home Screen
- **Categories**: Organized by LLM and Experimental
- **Task Cards**: Visual representation of available capabilities
- **Quick Actions**: Direct access to frequently used features
- **Settings Access**: App configuration and preferences

#### Navigation System
- **Task-based Navigation**: Each AI capability has dedicated screens
- **Model Manager**: Centralized model selection and management
- **Back Navigation**: Consistent navigation patterns throughout app

#### Chat Interface
- **Message Bubbles**: Distinct styling for user and AI responses
- **Media Support**: Images, audio clips, and text in conversations
- **Action Buttons**: Copy, regenerate, benchmark responses
- **Input Controls**: Text, camera, microphone inputs

### 4. Performance & Monitoring

#### Real-time Metrics
- **Inference Speed**: Tokens per second measurement
- **Latency Tracking**: Time to first token and total response time
- **Memory Usage**: RAM consumption monitoring
- **Battery Impact**: Optimization for device efficiency

#### Benchmarking Tools
- **Performance Testing**: Built-in benchmark capabilities
- **Iteration Controls**: Configurable test runs
- **Results Analysis**: Detailed performance reports
- **Comparison Tools**: Model performance comparison

### 5. Technical Architecture

#### On-Device Processing
- **Local Inference**: All AI processing happens on device
- **No Internet Required**: Works completely offline after model download
- **Privacy Focused**: No data leaves the device
- **Multiple Accelerators**: CPU and GPU support

#### Supported Technologies
- **MediaPipe Tasks**: GenAI, Text, and Image Generation
- **TensorFlow Lite**: Optimized model execution
- **Android CameraX**: Camera integration
- **Jetpack Compose**: Modern Android UI framework

### 6. Data Management

#### Storage System
- **Model Storage**: Efficient local model caching
- **User Preferences**: Theme, settings, and configurations
- **Chat History**: Conversation persistence
- **Import Management**: Custom model organization

#### Security Features
- **Encrypted Storage**: Sensitive data protection
- **Permission Management**: Camera, microphone, storage access
- **OAuth Integration**: Secure authentication (HuggingFace)
- **Terms of Service**: Built-in consent management

## Getting Started

### First Time Setup
1. **Launch App**: Open AI Edge Gallery
2. **Accept Terms**: Review and accept terms of service
3. **Choose Capability**: Select desired AI feature from home screen
4. **Download Model**: Pick and download required model
5. **Start Using**: Begin interacting with AI capabilities

### Model Selection Process
1. **Browse Tasks**: View available AI capabilities
2. **Model List**: See compatible models for each task
3. **Download**: Select model and wait for download completion
4. **Configuration**: Adjust model parameters if needed
5. **Initialize**: Model loads automatically when first used

### Basic Interaction Flow
1. **Input**: Provide text, image, or audio input
2. **Processing**: AI model processes input on-device
3. **Response**: View AI-generated response with metrics
4. **Actions**: Copy, regenerate, or benchmark results
5. **Continue**: Maintain conversation or start new session

## Troubleshooting

### Common Issues
- **Model Download Fails**: Check storage space and internet connection
- **Slow Performance**: Try switching to GPU accelerator or smaller model
- **Memory Warnings**: Close other apps or use lighter models
- **Camera/Mic Issues**: Check app permissions in device settings

### Performance Optimization
- **Model Selection**: Choose appropriate model size for device
- **Accelerator Choice**: GPU typically faster than CPU
- **Memory Management**: Close unused models to free resources
- **Background Apps**: Minimize other running applications

## Advanced Features

### Configuration Options
- **Theme Selection**: Light, dark, or system theme
- **Model Parameters**: Fine-tune generation settings
- **Debug Mode**: Access detailed performance information
- **Export Options**: Share results and configurations

### Developer Features
- **Benchmark Testing**: Systematic performance evaluation
- **Model Import**: Use custom trained models
- **API Integration**: Extend functionality with custom tasks
- **Analytics**: Firebase-based usage tracking

---

*For technical support or advanced configuration, refer to the Google AI Edge documentation or the project's GitHub repository.*
