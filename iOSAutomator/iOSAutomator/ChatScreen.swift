import SwiftUI

// MARK: - MessageView
struct MessageView: View {
    let message: ChatMessage

    var body: some View {
        HStack {
            if message.sender == .user {
                Spacer() // Push user messages to the right
            }

            VStack(alignment: message.sender == .user ? .trailing : .leading) {
                if message.isLoading {
                    HStack(spacing: 8) {
                        ProgressView()
                            .tint(message.sender == .user ? .white : .blue)
                        Text("Thinking...")
                            .font(.caption)
                            .foregroundColor(message.sender == .user ? .white.opacity(0.8) : .gray)
                    }
                    .padding()
                    .background(message.sender == .user ? Color.blue : Color(UIColor.systemGray5))
                    .cornerRadius(12)
                } else if message.isError {
                    Text(message.text)
                        .padding()
                        .foregroundColor(.white)
                        .background(Color.red)
                        .cornerRadius(12)
                        .contextMenu { // Allow copying error message
                            Button(action: {
                                UIPasteboard.general.string = message.text
                            }) {
                                Text("Copy")
                                Image(systemName: "doc.on.doc")
                            }
                        }
                } else {
                    Text(message.text)
                        .padding()
                        .foregroundColor(message.sender == .user ? .white : .black)
                        .background(message.sender == .user ? Color.blue : Color(UIColor.systemGray5))
                        .cornerRadius(12)
                        .contextMenu { // Allow copying message text
                             Button(action: {
                                 UIPasteboard.general.string = message.text
                             }) {
                                 Text("Copy")
                                 Image(systemName: "doc.on.doc")
                             }
                         }
                }
            }

            if message.sender == .model {
                Spacer() // Push model messages to the left
            }
        }
        .padding(.vertical, 4) // Add some vertical spacing between messages
    }
}

// MARK: - ChatScreen
struct ChatScreen: View {
    @StateObject var viewModel = ChatViewModel()
    @State private var userInput: String = ""
    @Namespace var bottomAnchorNamespace // Namespace for the scroll anchor

    var body: some View {
        VStack {
            // Message Display Area
            ScrollViewReader { scrollViewProxy in
                ScrollView {
                    LazyVStack(spacing: 8) { // Use LazyVStack for better performance with many messages
                        ForEach(viewModel.chatHistory) { message in
                            MessageView(message: message)
                                .id(message.id) // Make each message identifiable for scrolling
                        }
                        // Invisible anchor for auto-scrolling
                        Spacer()
                            .frame(height: 1)
                            .id("bottom_anchor")
                    }
                    .padding(.horizontal)
                }
                .onChange(of: viewModel.chatHistory) { _ in
                    // Auto-scroll to bottom when new messages are added
                    withAnimation {
                        scrollViewProxy.scrollTo("bottom_anchor", anchor: .bottom)
                    }
                }
                .onAppear { // Scroll to bottom on initial appear
                    withAnimation {
                        scrollViewProxy.scrollTo("bottom_anchor", anchor: .bottom)
                    }
                }
            }

            // Error Display
            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .foregroundColor(.red)
                    .padding(.horizontal)
                    .padding(.vertical, 5)
                    .background(Color.red.opacity(0.1))
                    .cornerRadius(8)
            }

            // Input Area
            HStack {
                TextField("Message Automator...", text: $userInput, axis: .vertical) // Allow multiline input
                    .textFieldStyle(.roundedBorder)
                    .lineLimit(1...5) // Allow up to 5 lines of text
                    .padding(.leading)

                Button(action: {
                    sendMessage()
                }) {
                    Image(systemName: "arrow.up.circle.fill")
                        .resizable()
                        .frame(width: 32, height: 32)
                        .foregroundColor(userInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isLoading ? .gray : .blue)
                }
                .disabled(userInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isLoading)
                .padding(.trailing)
                .padding(.bottom, 5) // Align with textfield padding
            }
            .padding(.bottom, 8) // Padding for the entire input HStack
            .padding(.top, 5)
        }
        .navigationTitle("iOS Automator")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func sendMessage() {
        let processedInput = userInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !processedInput.isEmpty else { return }

        viewModel.sendMessage(processedInput)
        userInput = ""
    }
}

// MARK: - Preview
struct ChatScreen_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView { // Wrap in NavigationView for previewing title
            ChatScreen()
        }
    }
}
