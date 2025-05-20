import SwiftUI
import FirebaseVertexAI // For generative model interaction

// MARK: - Sender Enum
enum Sender: Hashable {
    case user
    case model
}

// MARK: - ChatMessage Struct
struct ChatMessage: Identifiable, Hashable {
    let id: UUID
    var text: String
    let sender: Sender
    var isError: Bool = false
    var isLoading: Bool = false

    init(id: UUID = UUID(), text: String, sender: Sender, isError: Bool = false, isLoading: Bool = false) {
        self.id = id
        self.text = text
        self.sender = sender
        self.isError = isError
        self.isLoading = isLoading
    }
}

// MARK: - ChatViewModel Class
@MainActor // Ensures @Published properties are updated on the main thread
class ChatViewModel: ObservableObject {
    @Published var chatHistory: [ChatMessage] = []
    @Published var errorMessage: String? = nil
    @Published var isLoading: Bool = false

    // Initialize the generative model
    // Model name: "gemini-1.5-flash-preview-0514" is used as a widely available and capable model.
    // GenerationConfig: Based on common defaults from Gemini API documentation.
    // Temperature: 1 (Higher values like 1.0 will produce more creative results, lower values will be more deterministic)
    // TopP: 0.95 (Nucleus sampling)
    // TopK: 64 (Top-k sampling)
    // MaxOutputTokens: 8192 (Maximum number of tokens to generate in the response)
    // ResponseMIMEType: "text/plain" for plain text chat.
    private let generativeModel = VertexAI.vertexAI().generativeModel(
        modelName: "gemini-1.5-flash-preview-0514",
        generationConfig: GenerationConfig(
            temperature: 1,
            topP: 0.95,
            topK: 64,
            maxOutputTokens: 8192,
            responseMIMEType: "text/plain"
        )
    )

    init() {
        sendInitialMessage()
    }

    func sendInitialMessage() {
        let initialMessage = ChatMessage(text: "Automator is ready. How can I help?", sender: .model)
        chatHistory.append(initialMessage)
    }

    func sendMessage(_ prompt: String) {
        guard !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }

        // Add user's message to history
        let userMessage = ChatMessage(text: prompt, sender: .user)
        chatHistory.append(userMessage)

        // Set loading state
        isLoading = true
        errorMessage = nil

        // Add placeholder for model's response
        let placeholderId = UUID()
        let placeholderMessage = ChatMessage(id: placeholderId, text: "", sender: .model, isLoading: true)
        chatHistory.append(placeholderMessage)

        Task {
            do {
                let response = try await generativeModel.generateContent(prompt)

                // Remove placeholder
                chatHistory.removeAll { $0.id == placeholderId }

                if let modelResponseText = response.text {
                    let modelMessage = ChatMessage(text: modelResponseText, sender: .model)
                    chatHistory.append(modelMessage)
                } else {
                    // This case should ideally not happen if responseMIMEType is text/plain and model behaves
                    let errorText = "Received an empty response from the model."
                    errorMessage = errorText
                    chatHistory.append(ChatMessage(text: errorText, sender: .model, isError: true))
                }
            } catch {
                // Remove placeholder
                chatHistory.removeAll { $0.id == placeholderId }

                let errorDescription = "Error: \(error.localizedDescription)"
                errorMessage = errorDescription
                chatHistory.append(ChatMessage(text: errorDescription, sender: .model, isError: true))
            }
            // Reset loading state
            isLoading = false
        }
    }
}
